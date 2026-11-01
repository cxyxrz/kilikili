package com.kilikili.web.controller;

import com.kilikili.web.search.SearchSyncService;
import com.kilikili.web.search.VideoSearchIndexService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * 搜索索引运维接口。必须携带 X-Admin-Token，且配置了 kilikili.search.admin-token 才可用。
 */
@RestController
@RequestMapping("/api/search-admin")
public class SearchAdminController {
    private final VideoSearchIndexService indexService;
    private final SearchSyncService syncService;
    private final String adminToken;

    public SearchAdminController(VideoSearchIndexService indexService, SearchSyncService syncService,
                                 @Value("${kilikili.search.admin-token:}") String adminToken) {
        this.indexService = indexService;
        this.syncService = syncService;
        this.adminToken = adminToken;
    }

    /** 零停机重建索引：新索引灌数完成后原子切换别名 */
    @PostMapping("/reindex")
    public Map<String, Object> reindex(@RequestHeader(value = "X-Admin-Token", required = false) String token) throws Exception {
        requireAdmin(token);
        return indexService.rebuildIndex();
    }

    /** 立即执行一次 DB → ES 对账 */
    @PostMapping("/reconcile")
    public Map<String, Object> reconcile(@RequestHeader(value = "X-Admin-Token", required = false) String token) throws Exception {
        requireAdmin(token);
        return syncService.reconcile();
    }

    @GetMapping("/status")
    public Map<String, Object> status(@RequestHeader(value = "X-Admin-Token", required = false) String token) throws Exception {
        requireAdmin(token);
        return syncService.status();
    }

    private void requireAdmin(String token) {
        if (adminToken == null || adminToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "未配置 kilikili.search.admin-token，运维接口已禁用");
        }
        if (token == null || !MessageDigest.isEqual(
                adminToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权执行该操作");
        }
    }
}
