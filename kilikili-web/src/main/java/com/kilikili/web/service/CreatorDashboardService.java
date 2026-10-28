package com.kilikili.web.service;

import com.kilikili.common.storage.StorageService;
import com.kilikili.web.mapper.CreatorDashboardMapper;
import com.kilikili.web.mapper.FollowMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 创作中心数据看板 */
@Service
public class CreatorDashboardService {
    private final CreatorDashboardMapper dashboardMapper;
    private final FollowMapper followMapper;
    private final StorageService storageService;

    public CreatorDashboardService(CreatorDashboardMapper dashboardMapper, FollowMapper followMapper,
                                   StorageService storageService) {
        this.dashboardMapper = dashboardMapper;
        this.followMapper = followMapper;
        this.storageService = storageService;
    }

    public Map<String, Object> dashboard(long authorId, int days) {
        int span = days == 30 ? 30 : 7;
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(span - 1L);
        LocalDateTime start = from.atStartOfDay();

        Map<String, Object> totals = dashboardMapper.totals(authorId);
        Map<String, Object> periodTotals = dashboardMapper.periodTotals(authorId, start);

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("videoCount", num(totals, "videoCount"));
        overview.put("viewCount", num(totals, "viewCount"));
        overview.put("likeCount", num(totals, "likeCount"));
        overview.put("commentCount", num(totals, "commentCount"));
        overview.put("favoriteCount", dashboardMapper.favoriteCount(authorId));
        overview.put("followerCount", followMapper.countFollowers(authorId));

        Map<String, Object> period = new LinkedHashMap<>();
        period.put("views", num(periodTotals, "views"));
        period.put("likes", num(periodTotals, "likes"));
        period.put("comments", num(periodTotals, "comments"));
        period.put("favorites", num(periodTotals, "favorites"));
        period.put("followers", num(periodTotals, "followers"));

        Map<String, Map<String, Long>> series = new HashMap<>();
        series.put("views", toDayMap(dashboardMapper.dailyViews(authorId, start)));
        series.put("likes", toDayMap(dashboardMapper.dailyLikes(authorId, start)));
        series.put("comments", toDayMap(dashboardMapper.dailyComments(authorId, start)));
        series.put("followers", toDayMap(dashboardMapper.dailyFollowers(authorId, start)));
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 0; i < span; i++) {
            String day = from.plusDays(i).toString();
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", day);
            point.put("label", day.substring(5));
            for (String key : List.of("views", "likes", "comments", "followers")) {
                point.put(key, series.get(key).getOrDefault(day, 0L));
            }
            trend.add(point);
        }

        Map<String, Object> audience = new LinkedHashMap<>();
        audience.put("gender", labels(dashboardMapper.followerGenders(authorId), "name", CreatorDashboardService::genderLabel));
        audience.put("age", labels(dashboardMapper.followerAges(authorId), "bucket", CreatorDashboardService::ageLabel));
        List<Map<String, Object>> hours = new ArrayList<>();
        Map<Integer, Long> hourMap = new HashMap<>();
        for (Map<String, Object> row : dashboardMapper.viewHours(authorId)) {
            hourMap.put((int) num(row, "hr"), num(row, "cnt"));
        }
        for (int hour = 0; hour < 24; hour++) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("hour", hour);
            point.put("count", hourMap.getOrDefault(hour, 0L));
            hours.add(point);
        }
        audience.put("hours", hours);

        List<Map<String, Object>> videos = new ArrayList<>();
        for (Map<String, Object> row : dashboardMapper.videoComparison(authorId)) {
            long views = num(row, "view_count");
            long likes = num(row, "like_count");
            long comments = num(row, "comment_count");
            long favorites = num(row, "favorite_count");
            Map<String, Object> video = new LinkedHashMap<>();
            video.put("id", num(row, "id"));
            video.put("title", row.get("title"));
            String cover = (String) row.get("cover_object_key");
            video.put("coverUrl", cover == null || cover.isBlank() ? null : storageService.presignedReadUrl(cover));
            video.put("viewCount", views);
            video.put("likeCount", likes);
            video.put("commentCount", comments);
            video.put("favoriteCount", favorites);
            video.put("createdAt", row.get("created_at"));
            video.put("interactionRate", views == 0 ? 0D : Math.round((likes + comments + favorites) * 1000D / views) / 10D);
            videos.add(video);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", span);
        result.put("overview", overview);
        result.put("period", period);
        result.put("trend", trend);
        result.put("audience", audience);
        result.put("videos", videos);
        return result;
    }

    private static Map<String, Long> toDayMap(List<Map<String, Object>> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object day = row.get("day");
            if (day != null) map.put(day.toString(), num(row, "cnt"));
        }
        return map;
    }

    private static List<Map<String, Object>> labels(List<Map<String, Object>> rows, String nameKey,
                                                    java.util.function.Function<String, String> labeler) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object raw = row.get(nameKey);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", labeler.apply(raw == null ? null : raw.toString()));
            item.put("count", num(row, "cnt"));
            items.add(item);
        }
        items.sort((left, right) -> Long.compare((Long) right.get("count"), (Long) left.get("count")));
        return items;
    }

    private static String genderLabel(String gender) {
        if (gender == null) return "未设置";
        return switch (gender) {
            case "MALE" -> "男";
            case "FEMALE" -> "女";
            default -> "未设置";
        };
    }

    private static String ageLabel(String bucket) {
        if (bucket == null || "UNKNOWN".equals(bucket)) return "未填写生日";
        return switch (bucket) {
            case "UNDER18" -> "18 岁以下";
            case "18-24" -> "18-24 岁";
            case "25-34" -> "25-34 岁";
            case "35-44" -> "35-44 岁";
            default -> "45 岁以上";
        };
    }

    /** 兼容不同大小写形式的列名 */
    private static long num(Map<String, Object> row, String key) {
        if (row == null) return 0L;
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key) && entry.getValue() != null) {
                return ((Number) entry.getValue()).longValue();
            }
        }
        return 0L;
    }
}
