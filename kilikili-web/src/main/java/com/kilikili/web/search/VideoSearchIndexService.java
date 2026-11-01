package com.kilikili.web.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.update_aliases.Action;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 视频搜索索引维护。
 * <ul>
 *   <li><b>别名</b>：对外统一读写 {@code kilikili_video} 这个别名，真实数据落在带时间戳的物理索引上。</li>
 *   <li><b>零停机重建</b>：先建新物理索引 → 全量灌数 → 原子切换别名，切换期间搜索不受影响，旧索引保留用于回滚。</li>
 *   <li><b>兼容旧库</b>：启动时如果发现同名物理索引（旧版本直接建索引），会自动迁移为别名模式。</li>
 * </ul>
 * Elasticsearch 不可用时索引与搜索整体降级为 SQL LIKE，不影响主流程。
 */
@Service
public class VideoSearchIndexService {
    private static final Logger log = LoggerFactory.getLogger(VideoSearchIndexService.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter INDEX_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final int SYNC_LIMIT = 100_000;
    private static final int DELETED_CHECK_SIZE = 5000;
    private static final int INDEXED_IDS_LIMIT = 10_000;

    /**
     * 分词策略：标准分词器先按字切分中日韩文字，cjk_bigram 负责重组中文二元组（中文检索可用），
     * synonym 在二元组之后做同义词扩展，ngram(2-3) 负责英文/数字片段（支持「jav」这类前缀匹配）。
     * 无需安装 IK 插件。
     */
    private static final String INDEX_BODY = """
            {
              "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0,
                "analysis": {
                  "filter": {
                    "text_ngram": {"type": "ngram", "min_gram": 2, "max_gram": 3},
                    "text_synonym": {
                      "type": "synonym",
                      "synonyms": [
                        "动漫,动画",
                        "二次元,acg",
                        "教程,教学,课程",
                        "剪辑,后期",
                        "编程,代码",
                        "数码,科技",
                        "游戏,电竞",
                        "音乐,歌曲",
                        "美食,吃播",
                        "搞笑,鬼畜",
                        "运动,健身",
                        "旅行,旅游"
                      ]
                    }
                  },
                  "analyzer": {
                    "text_index": {
                      "type": "custom",
                      "tokenizer": "standard",
                      "filter": ["cjk_width", "lowercase", "cjk_bigram", "text_synonym", "text_ngram"]
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
    private final int keepOldIndices;
    private volatile boolean available = false;
    private volatile long lastCheckAt = 0L;

    public VideoSearchIndexService(ElasticsearchClient client, VideoMapper videoMapper,
                                   @Value("${kilikili.search.index:kilikili_video}") String indexName,
                                   @Value("${kilikili.search.enabled:true}") boolean enabled,
                                   @Value("${kilikili.search.keep-old-indices:1}") int keepOldIndices) {
        this.client = client;
        this.videoMapper = videoMapper;
        this.indexName = indexName;
        this.enabled = enabled;
        this.keepOldIndices = Math.max(0, keepOldIndices);
    }

    public boolean enabled() {
        return enabled;
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
            log.info("搜索索引初始化完成: alias={}, 物理索引={}, 文档数={}", indexName, physicalIndices(), synced);
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

    /** 补偿任务/对账用：索引失败必须抛出，由调用方记录任务并重试 */
    public void indexVideoStrict(long videoId) {
        if (!enabled) return;
        Video video = videoMapper.findById(videoId);
        if (video == null || !"PUBLISHED".equals(video.status())) {
            deleteVideoStrict(videoId);
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
            deleteById(videoId);
            client.indices().refresh(refresh -> refresh.index(indexName));
        } catch (Exception exception) {
            available = false;
            log.warn("搜索索引删除失败: videoId={}, {}", videoId, exception.getMessage());
        }
    }

    /** 补偿任务/对账用：删除失败（404 除外）必须抛出，让任务重试 */
    public void deleteVideoStrict(long videoId) {
        if (!enabled) return;
        try {
            deleteById(videoId);
            client.indices().refresh(refresh -> refresh.index(indexName));
        } catch (ElasticsearchException exception) {
            if (exception.status() == 404) return;
            available = false;
            throw new IllegalStateException("搜索索引删除失败: " + exception.getMessage(), exception);
        } catch (Exception exception) {
            available = false;
            throw new IllegalStateException("搜索索引删除失败: " + exception.getMessage(), exception);
        }
    }

    private void deleteById(long videoId) throws Exception {
        client.delete(request -> request.index(indexName).id(String.valueOf(videoId)));
    }

    /** 与数据库全量对齐：补齐缺失文档、移除已非发布状态的文档 */
    public int syncAll() throws Exception {
        return syncAllTo(indexName);
    }

    private int syncAllTo(String target) throws Exception {
        List<Video> videos = videoMapper.listPublished(SYNC_LIMIT);
        List<BulkOperation> operations = new ArrayList<>(videos.size());
        Set<String> liveIds = new HashSet<>(videos.size());
        for (Video video : videos) {
            liveIds.add(String.valueOf(video.id()));
            operations.add(BulkOperation.of(operation -> operation.index(index -> index
                    .index(target).id(String.valueOf(video.id())).document(toDocument(video)))));
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
        removeStale(target, liveIds);
        client.indices().refresh(refresh -> refresh.index(target));
        return videos.size();
    }

    private void removeStale(String target, Set<String> liveIds) throws Exception {
        try {
            SearchResponse<Map> response = client.search(request -> request
                    .index(target)
                    .size(DELETED_CHECK_SIZE)
                    .source(source -> source.fetch(false))
                    .query(query -> query.matchAll(matchAll -> matchAll)), Map.class);
            List<BulkOperation> deletes = new ArrayList<>();
            for (var hit : response.hits().hits()) {
                if (!liveIds.contains(hit.id())) {
                    deletes.add(BulkOperation.of(operation -> operation.delete(del -> del.index(target).id(hit.id()))));
                }
            }
            if (!deletes.isEmpty()) {
                client.bulk(request -> {
                    request.index(target);
                    deletes.forEach(request::operations);
                    return request;
                });
                log.info("搜索索引移除失效文档 {} 条", deletes.size());
            }
        } catch (Exception exception) {
            log.warn("搜索索引清理失效文档失败: {}", exception.getMessage());
        }
    }

    /** 读取当前别名下全部文档 id，供对账任务比对 */
    public Set<String> listIndexedIds() throws Exception {
        SearchResponse<Map> response = client.search(request -> request
                        .index(indexName)
                        .size(INDEXED_IDS_LIMIT)
                        .source(source -> source.fetch(false))
                        .query(query -> query.matchAll(matchAll -> matchAll)),
                Map.class);
        Set<String> ids = new LinkedHashSet<>();
        for (var hit : response.hits().hits()) ids.add(hit.id());
        if (ids.size() >= INDEXED_IDS_LIMIT) {
            log.warn("对账读取 ES 文档 id 达到上限 {}，更大数据量请改用 scroll/search_after", INDEXED_IDS_LIMIT);
        }
        return ids;
    }

    /** 当前别名是否已存在（供状态接口判断） */
    public boolean aliasExists() throws Exception {
        return client.indices().existsAlias(request -> request.name(indexName)).value();
    }

    /** 当前别名指向的物理索引 */
    public List<String> physicalIndices() throws Exception {
        GetAliasResponse response = client.indices().getAlias(request -> request.name(indexName));
        return new ArrayList<>(response.result().keySet());
    }

    public long documentCount() throws Exception {
        return client.count(request -> request.index(indexName)).count();
    }

    /**
     * 零停机重建：新建物理索引并全量灌数，完成后原子切换别名，旧索引默认保留一份用于回滚。
     * 重建期间搜索继续命中旧索引；切换后的写入全部落到新索引。
     */
    public synchronized Map<String, Object> rebuildIndex() throws Exception {
        if (!enabled) throw new IllegalStateException("搜索索引已关闭");
        String newIndex = nextPhysicalName();
        List<String> previous = List.of();
        try {
            previous = aliasExists() ? physicalIndices() : List.of();
        } catch (Exception exception) {
            log.warn("读取当前别名指向失败，按首次重建处理: {}", exception.getMessage());
        }
        createPhysicalIndex(newIndex);
        try {
            int synced = syncAllTo(newIndex);
            client.indices().refresh(refresh -> refresh.index(newIndex));
            switchAlias(newIndex, previous);
            // 切换后补一次全量，覆盖重建期间写入旧索引的数据
            try {
                syncAll();
            } catch (Exception exception) {
                log.warn("重建切换后的补偿同步失败，将由定时同步修复: {}", exception.getMessage());
            }
            String newPhysical = newIndex;
            try {
                cleanupOldIndices(newPhysical);
            } catch (Exception exception) {
                log.warn("清理历史索引失败: {}", exception.getMessage());
            }
            log.info("搜索索引零停机重建完成: alias={}, new={}, previous={}, 文档数={}", indexName, newIndex, previous, synced);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("alias", indexName);
            result.put("index", newIndex);
            result.put("synced", synced);
            result.put("previous", previous);
            return result;
        } catch (Exception exception) {
            deleteIndexQuietly(newIndex);
            throw exception;
        }
    }

    private void switchAlias(String newIndex, List<String> previous) throws Exception {
        List<Action> actions = new ArrayList<>();
        actions.add(Action.of(action -> action.add(add -> add.index(newIndex).alias(indexName))));
        for (String old : previous) {
            if (old.equals(newIndex)) continue;
            actions.add(Action.of(action -> action.remove(remove -> remove.index(old).alias(indexName))));
        }
        client.indices().updateAliases(request -> request.actions(actions));
    }

    private void cleanupOldIndices(String current) throws Exception {
        String prefix = indexName + "_";
        Set<String> all = new HashSet<>(client.indices().get(request -> request.index(prefix + "*")).result().keySet());
        all.remove(current);
        List<String> old = new ArrayList<>(all);
        old.sort(Comparator.reverseOrder());
        for (int i = keepOldIndices; i < old.size(); i++) {
            deleteIndexQuietly(old.get(i));
            log.info("已清理历史搜索索引: {}", old.get(i));
        }
    }

    private String nextPhysicalName() {
        return indexName + "_" + LocalDateTime.now().format(INDEX_TIME_FORMAT);
    }

    private void createPhysicalIndex(String name) throws Exception {
        client.indices().create(request -> request.index(name).withJson(new StringReader(INDEX_BODY)));
        log.info("搜索物理索引已创建: {}", name);
    }

    private void deleteIndexQuietly(String name) {
        try {
            client.indices().delete(request -> request.index(name));
        } catch (ElasticsearchException exception) {
            if (exception.status() != 404) log.warn("删除物理索引失败: index={}, {}", name, exception.getMessage());
        } catch (Exception exception) {
            log.warn("删除物理索引失败: index={}, {}", name, exception.getMessage());
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

    /**
     * 确保别名存在；返回 true 表示 Elasticsearch 可用。
     * 旧版本直接以 {@code kilikili_video} 为物理索引，这里会自动迁移：新索引灌完数据后原子删除旧索引并绑定别名。
     */
    private synchronized boolean ensureIndex() throws Exception {
        if (aliasExists()) {
            available = true;
            lastCheckAt = System.currentTimeMillis();
            return true;
        }
        if (client.indices().exists(request -> request.index(indexName)).value()) {
            migrateLegacyIndex();
            available = true;
            lastCheckAt = System.currentTimeMillis();
            return true;
        }
        String physical = nextPhysicalName();
        createPhysicalIndex(physical);
        client.indices().updateAliases(request -> request
                .actions(action -> action.add(add -> add.index(physical).alias(indexName))));
        log.info("搜索索引已创建并绑定别名: alias={}, index={}", indexName, physical);
        available = true;
        lastCheckAt = System.currentTimeMillis();
        return true;
    }

    private void migrateLegacyIndex() throws Exception {
        String legacy = indexName;
        String physical = nextPhysicalName();
        log.info("检测到旧版物理索引，开始迁移为别名模式: old={}, new={}", legacy, physical);
        createPhysicalIndex(physical);
        try {
            int synced = syncAllTo(physical);
            client.indices().refresh(refresh -> refresh.index(physical));
            // 同一个请求内先删旧索引再挂别名，避免与旧索引重名冲突
            List<Action> actions = new ArrayList<>();
            actions.add(Action.of(action -> action.removeIndex(remove -> remove.index(legacy))));
            actions.add(Action.of(action -> action.add(add -> add.index(physical).alias(indexName))));
            client.indices().updateAliases(request -> request.actions(actions));
            log.info("旧索引迁移完成: alias={}, index={}, 文档数={}", indexName, physical, synced);
        } catch (Exception exception) {
            deleteIndexQuietly(physical);
            throw exception;
        }
    }
}
