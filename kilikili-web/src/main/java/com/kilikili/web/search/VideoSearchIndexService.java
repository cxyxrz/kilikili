package com.kilikili.web.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.kilikili.web.entity.Video;
import com.kilikili.web.mapper.VideoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.StringReader;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 视频搜索索引维护：启动时建索引并全量同步，之后定时全量校准；投稿发布/删除时即时更新单条文档。
 * Elasticsearch 不可用时索引与搜索整体降级为 SQL LIKE，不影响主流程。
 */
@Service
public class VideoSearchIndexService {
    private static final Logger log = LoggerFactory.getLogger(VideoSearchIndexService.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int SYNC_LIMIT = 100_000;
    private static final int DELETED_CHECK_SIZE = 5000;

    /**
     * 分词策略：标准分词器先按字切分中日韩文字，cjk_bigram 负责重组中文二元组（中文检索可用），
     * ngram(2-3) 负责英文/数字片段（支持「jav」这类前缀匹配）。无需安装 IK 插件。
     */
    private static final String INDEX_BODY = """
            {
              "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0,
                "analysis": {
                  "filter": {
                    "text_ngram": {"type": "ngram", "min_gram": 2, "max_gram": 3}
                  },
                  "analyzer": {
                    "text_index": {
                      "type": "custom",
                      "tokenizer": "standard",
                      "filter": ["cjk_width", "lowercase", "cjk_bigram", "text_ngram"]
                    }
                  }
                }
              },
              "mappings": {
                "properties": {
                  "title": {"type": "text", "analyzer": "text_index", "search_analyzer": "text_index"},
                  "description": {"type": "text", "analyzer": "text_index", "search_analyzer": "text_index"},
                  "authorName": {"type": "text", "analyzer": "text_index", "search_analyzer": "text_index"},
                  "authorId": {"type": "long"},
                  "categoryId": {"type": "long"},
                  "coverObjectKey": {"type": "keyword", "index": false},
                  "durationSeconds": {"type": "integer"},
                  "viewCount": {"type": "long"},
                  "likeCount": {"type": "long"},
                  "commentCount": {"type": "long"},
                  "createdAt": {"type": "date", "format": "yyyy-MM-dd'T'HH:mm:ss||yyyy-MM-dd HH:mm:ss||epoch_millis"},
                  "status": {"type": "keyword"},
                  "suggest": {"type": "completion", "analyzer": "text_index", "search_analyzer": "text_index"}
                }
              }
            }
            """;

    private final ElasticsearchClient client;
    private final VideoMapper videoMapper;
    private final String indexName;
    private final boolean enabled;
    private volatile boolean available = false;
    private volatile long lastCheckAt = 0L;

    public VideoSearchIndexService(ElasticsearchClient client, VideoMapper videoMapper,
                                   @Value("${kilikili.search.index:kilikili_video}") String indexName,
                                   @Value("${kilikili.search.enabled:true}") boolean enabled) {
        this.client = client;
        this.videoMapper = videoMapper;
        this.indexName = indexName;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        if (!enabled) {
            log.info("Elasticsearch 搜索索引已关闭（kilikili.search.enabled=false），搜索降级为数据库查询");
            return;
        }
        try {
            ensureIndex();
            int synced = syncAll();
            log.info("搜索索引初始化完成: index={}, 文档数={}", indexName, synced);
        } catch (Exception exception) {
            available = false;
            log.warn("搜索索引初始化失败，搜索将降级为数据库查询: {}", exception.getMessage());
        }
    }

    @Scheduled(initialDelayString = "${kilikili.search.initial-delay-ms:8000}",
            fixedDelayString = "${kilikili.search.sync-interval-ms:120000}")
    public void scheduledSync() {
        if (!enabled) return;
        try {
            if (ensureIndex()) syncAll();
        } catch (Exception exception) {
            available = false;
            log.warn("搜索索引定时同步失败: {}", exception.getMessage());
        }
    }

    public boolean available() {
        if (!enabled) return false;
        // 30 秒内复用上一次探测结果，避免每次检索都 ping
        if (System.currentTimeMillis() - lastCheckAt < 30_000) return available;
        lastCheckAt = System.currentTimeMillis();
        try {
            available = client.ping().value();
        } catch (Exception exception) {
            available = false;
        }
        return available;
    }

    /** 事务内调用时延迟到提交后建索引，避免把未提交数据写入索引 */
    public void indexAfterCommit(long videoId) {
        if (!enabled) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    indexVideo(videoId);
                }
            });
        } else {
            indexVideo(videoId);
        }
    }

    public void indexVideo(long videoId) {
        if (!enabled) return;
        try {
            Video video = videoMapper.findById(videoId);
            if (video == null || !"PUBLISHED".equals(video.status())) {
                deleteVideo(videoId);
                return;
            }
            client.index(request -> request.index(indexName).id(String.valueOf(videoId)).document(toDocument(video)));
        } catch (Exception exception) {
            available = false;
            log.warn("搜索索引写入失败: videoId={}, {}", videoId, exception.getMessage());
        }
    }

    /** MQ 消费用：索引失败必须抛出，让消息重试；成功后阶段才算完成 */
    public void indexVideoStrict(long videoId) {
        if (!enabled) return;
        Video video = videoMapper.findById(videoId);
        if (video == null || !"PUBLISHED".equals(video.status())) {
            deleteVideo(videoId);
            return;
        }
        try {
            client.index(request -> request.index(indexName).id(String.valueOf(videoId)).document(toDocument(video)));
            client.indices().refresh(refresh -> refresh.index(indexName));
        } catch (Exception exception) {
            available = false;
            throw new IllegalStateException("搜索索引写入失败: " + exception.getMessage(), exception);
        }
    }

    public void deleteVideo(long videoId) {
        if (!enabled) return;
        try {
            client.delete(request -> request.index(indexName).id(String.valueOf(videoId)));
            client.indices().refresh(refresh -> refresh.index(indexName));
        } catch (ElasticsearchException exception) {
            if (exception.status() != 404) log.warn("搜索索引删除失败: videoId={}, {}", videoId, exception.getMessage());
        } catch (Exception exception) {
            available = false;
            log.warn("搜索索引删除失败: videoId={}, {}", videoId, exception.getMessage());
        }
    }

    /** 与数据库全量对齐：补齐缺失文档、移除已非发布状态的文档 */
    public int syncAll() throws Exception {
        List<Video> videos = videoMapper.listPublished(SYNC_LIMIT);
        List<BulkOperation> operations = new ArrayList<>(videos.size());
        Set<String> liveIds = new HashSet<>(videos.size());
        for (Video video : videos) {
            liveIds.add(String.valueOf(video.id()));
            operations.add(BulkOperation.of(operation -> operation.index(index -> index
                    .index(indexName).id(String.valueOf(video.id())).document(toDocument(video)))));
        }
        if (!operations.isEmpty()) {
            BulkRequest.Builder bulk = new BulkRequest.Builder();
            operations.forEach(bulk::operations);
            BulkResponse response = client.bulk(bulk.build());
            if (response.errors()) {
                for (BulkResponseItem item : response.items()) {
                    if (item.error() != null) log.warn("搜索索引批量写入失败: id={}, {}", item.id(), item.error().reason());
                }
            }
        }
        removeStale(liveIds);
        client.indices().refresh(refresh -> refresh.index(indexName));
        return videos.size();
    }

    private void removeStale(Set<String> liveIds) throws Exception {
        try {
            SearchResponse<Map> response = client.search(request -> request
                    .index(indexName)
                    .size(DELETED_CHECK_SIZE)
                    .source(source -> source.fetch(false))
                    .query(query -> query.matchAll(matchAll -> matchAll)), Map.class);
            List<BulkOperation> deletes = new ArrayList<>();
            for (var hit : response.hits().hits()) {
                if (!liveIds.contains(hit.id())) {
                    deletes.add(BulkOperation.of(operation -> operation.delete(del -> del.index(indexName).id(hit.id()))));
                }
            }
            if (!deletes.isEmpty()) {
                client.bulk(request -> {
                    request.index(indexName);
                    deletes.forEach(request::operations);
                    return request;
                });
                log.info("搜索索引移除失效文档 {} 条", deletes.size());
            }
        } catch (Exception exception) {
            log.warn("搜索索引清理失效文档失败: {}", exception.getMessage());
        }
    }

    private Map<String, Object> toDocument(Video video) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("title", video.title());
        document.put("description", video.description() == null ? "" : video.description());
        document.put("authorName", video.authorName() == null ? "" : video.authorName());
        document.put("authorId", video.authorId());
        document.put("categoryId", video.categoryId());
        document.put("coverObjectKey", video.coverObjectKey());
        document.put("durationSeconds", video.durationSeconds() == null ? 0 : video.durationSeconds());
        document.put("viewCount", video.viewCount() == null ? 0 : video.viewCount());
        document.put("likeCount", video.likeCount() == null ? 0 : video.likeCount());
        document.put("commentCount", video.commentCount() == null ? 0 : video.commentCount());
        document.put("createdAt", video.createdAt() == null ? null : TIME_FORMAT.format(video.createdAt()));
        document.put("status", video.status());
        // 联想词：标题与 UP 主昵称，权重取播放量让热门内容优先
        Map<String, Object> suggest = new LinkedHashMap<>();
        List<String> inputs = new ArrayList<>();
        if (video.title() != null && !video.title().isBlank()) inputs.add(video.title());
        if (video.authorName() != null && !video.authorName().isBlank()) inputs.add(video.authorName());
        suggest.put("input", inputs);
        suggest.put("weight", video.viewCount() == null ? 0 : Math.min(video.viewCount(), 1_000_000));
        document.put("suggest", suggest);
        return document;
    }

    /** 确保索引存在；返回 true 表示 Elasticsearch 可用 */
    private boolean ensureIndex() throws Exception {
        boolean exists = client.indices().exists(request -> request.index(indexName)).value();
        if (!exists) {
            client.indices().create(request -> request.index(indexName).withJson(new StringReader(INDEX_BODY)));
            log.info("搜索索引已创建: {}", indexName);
        }
        available = true;
        lastCheckAt = System.currentTimeMillis();
        return true;
    }
}
