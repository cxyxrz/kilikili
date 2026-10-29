package com.kilikili.web.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggestOption;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import com.kilikili.common.storage.StorageService;
import com.kilikili.web.entity.Category;
import com.kilikili.web.entity.RecommendItem;
import com.kilikili.web.mapper.RecommendationMapper;
import com.kilikili.web.mapper.VideoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 视频搜索：优先走 Elasticsearch（分词 + 相关性排序），不可用时降级到数据库 LIKE。
 * 同时维护 Redis 热搜榜与搜索联想。
 */
@Service
public class VideoSearchService {
    private static final Logger log = LoggerFactory.getLogger(VideoSearchService.class);
    private static final String HOT_KEY = "kilikili:search:hot";
    private static final int HOT_LIMIT = 10;
    private static final int SUGGEST_LIMIT = 8;

    private final ElasticsearchClient client;
    private final VideoSearchIndexService indexService;
    private final RecommendationMapper recommendationMapper;
    private final VideoMapper videoMapper;
    private final StorageService storageService;
    private final StringRedisTemplate redisTemplate;
    private final String indexName;

    public VideoSearchService(ElasticsearchClient client, VideoSearchIndexService indexService,
                              RecommendationMapper recommendationMapper, VideoMapper videoMapper,
                              StorageService storageService, StringRedisTemplate redisTemplate,
                              @Value("${kilikili.search.index:kilikili_video}") String indexName) {
        this.client = client;
        this.indexService = indexService;
        this.recommendationMapper = recommendationMapper;
        this.videoMapper = videoMapper;
        this.storageService = storageService;
        this.redisTemplate = redisTemplate;
        this.indexName = indexName;
    }

    public Map<String, Object> search(String keyword, int page, int pageSize) {
        String normalized = normalizeKeyword(keyword);
        if (normalized.isEmpty()) {
            return Map.of("items", List.of(), "page", page, "pageSize", pageSize, "total", 0L, "hasMore", false);
        }
        recordKeyword(normalized);
        // 单字检索：ngram 最小长度为 2，这类关键词交给数据库 LIKE 处理
        if (normalized.length() < 2) return databaseSearch(normalized, page, pageSize);
        if (!indexService.available()) return databaseSearch(normalized, page, pageSize);
        try {
            return elasticSearch(normalized, page, pageSize);
        } catch (Exception exception) {
            log.warn("Elasticsearch 检索失败，降级为数据库查询: {}", exception.getMessage());
            return databaseSearch(normalized, page, pageSize);
        }
    }

    /** 搜索联想：Elasticsearch completion suggester，失败时退回标题前缀查询 */
    public List<String> suggest(String keyword) {
        String normalized = normalizeKeyword(keyword);
        if (normalized.isEmpty()) return List.of();
        if (indexService.available()) {
            try {
                List<String> suggestions = elasticSuggest(normalized);
                if (!suggestions.isEmpty()) return suggestions;
            } catch (Exception exception) {
                log.warn("搜索联想失败，降级为数据库前缀查询: {}", exception.getMessage());
            }
        }
        return videoMapper.suggestTitles(normalized, SUGGEST_LIMIT);
    }

    /** 记录搜索词到热搜榜（Redis 有序集合），并返回当前热搜 */
    public void recordKeyword(String keyword) {
        String normalized = normalizeKeyword(keyword);
        if (normalized.isEmpty()) return;
        try {
            redisTemplate.opsForZSet().incrementScore(HOT_KEY, normalized, 1);
        } catch (Exception exception) {
            log.warn("热搜记录失败: {}", exception.getMessage());
        }
    }

    public List<Map<String, Object>> hotKeywords() {
        Set<String> keywords = new LinkedHashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            Set<String> top = redisTemplate.opsForZSet().reverseRange(HOT_KEY, 0, HOT_LIMIT - 1);
            if (top != null) {
                for (String keyword : top) {
                    Double score = redisTemplate.opsForZSet().score(HOT_KEY, keyword);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("keyword", keyword);
                    item.put("score", score == null ? 0 : score.longValue());
                    result.add(item);
                    keywords.add(keyword);
                }
            }
        } catch (Exception exception) {
            log.warn("热搜读取失败: {}", exception.getMessage());
        }
        // 热搜不足时用分区名称补位，保证首屏有内容
        if (result.size() < HOT_LIMIT) {
            List<Category> categories = recommendationMapper.listCategories();
            for (Category category : categories) {
                if (result.size() >= HOT_LIMIT) break;
                if (category.name() == null || keywords.contains(category.name())) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("keyword", category.name());
                item.put("score", 0);
                result.add(item);
            }
        }
        for (int i = 0; i < result.size(); i++) result.get(i).put("rank", i + 1);
        return result;
    }

    private Map<String, Object> elasticSearch(String keyword, int page, int pageSize) throws Exception {
        int from = (page - 1) * pageSize;
        SearchResponse<Map> response = client.search(request -> request
                        .index(indexName)
                        .from(from)
                        .size(pageSize)
                        .query(query -> query.bool(bool -> bool
                                .must(must -> must.multiMatch(multi -> multi
                                        .query(keyword)
                                        .fields("title^3", "authorName^2", "description")
                                        .type(TextQueryType.BestFields)))
                                .filter(filter -> filter.term(term -> term.field("status").value("PUBLISHED")))))
                        .sort(sort -> sort.score(score -> score.order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
                        .sort(sort -> sort.field(field -> field.field("viewCount").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
                        .trackTotalHits(total -> total.enabled(true)),
                Map.class);
        List<RecommendItem> items = new ArrayList<>();
        for (Hit<Map> hit : response.hits().hits()) {
            if (hit.source() == null) continue;
            items.add(toItem(hit.id(), hit.source()));
        }
        long total = response.hits().total() == null ? items.size() : response.hits().total().value();
        return Map.of("items", items, "page", page, "pageSize", pageSize, "total", total,
                "hasMore", (long) page * pageSize < total, "engine", "elasticsearch");
    }

    private Map<String, Object> databaseSearch(String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<RecommendItem> items = recommendationMapper.search(keyword, pageSize, offset).stream()
                .map(source -> new RecommendItem(source.id(), source.title(), source.authorName(),
                        coverUrl(source.coverObjectKey()), source.durationSeconds(), source.viewCount(),
                        source.likeCount(), source.commentCount(), source.categoryId()))
                .toList();
        long total = recommendationMapper.countSearch(keyword);
        return Map.of("items", items, "page", page, "pageSize", pageSize, "total", total,
                "hasMore", (long) page * pageSize < total, "engine", "database");
    }

    private List<String> elasticSuggest(String keyword) throws Exception {
        SearchResponse<Map> response = client.search(request -> request
                        .index(indexName)
                        .suggest(suggest -> suggest.suggesters("keywords", suggester -> suggester
                                .prefix(keyword)
                                .completion(completion -> completion
                                        .field("suggest")
                                        .size(SUGGEST_LIMIT)
                                        .skipDuplicates(true)
                                        .fuzzy(fuzzy -> fuzzy.fuzziness("AUTO"))))),
                Map.class);
        Set<String> suggestions = new LinkedHashSet<>();
        List<Suggestion<Map>> groups = response.suggest().get("keywords");
        if (groups != null) {
            for (Suggestion<Map> group : groups) {
                if (!group.isCompletion()) continue;
                for (CompletionSuggestOption<Map> option : group.completion().options()) {
                    String text = option.text();
                    if (text != null && !text.isBlank() && suggestions.size() < SUGGEST_LIMIT) suggestions.add(text);
                }
            }
        }
        return new ArrayList<>(suggestions);
    }

    private RecommendItem toItem(String id, Map<String, Object> source) {
        return new RecommendItem(Long.parseLong(id), text(source.get("title")), text(source.get("authorName")),
                coverUrl(text(source.get("coverObjectKey"))), number(source.get("durationSeconds")).intValue(),
                number(source.get("viewCount")), number(source.get("likeCount")),
                number(source.get("commentCount")), number(source.get("categoryId")));
    }

    private String coverUrl(String objectKey) {
        return objectKey == null || objectKey.isBlank() ? null : storageService.presignedReadUrl(objectKey);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static Long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return 0L;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) return "";
        String normalized = keyword.trim();
        return normalized.length() > 40 ? normalized.substring(0, 40) : normalized;
    }
}
