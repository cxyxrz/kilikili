package com.kilikili.admin.service;

import com.kilikili.common.exception.BusinessException;
import com.kilikili.admin.mapper.AdminMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminService {
    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private static final Set<String> VIDEO_STATUSES = Set.of("PENDING_REVIEW", "PUBLISHED", "REJECTED", "OFFLINE");
    private final AdminMapper adminMapper;
    private final StringRedisTemplate redisTemplate;
    public AdminService(AdminMapper adminMapper, StringRedisTemplate redisTemplate) { this.adminMapper = adminMapper; this.redisTemplate = redisTemplate; }
    public Map<String, Object> dashboard() { return adminMapper.dashboard(); }
    public List<Map<String, Object>> videos(String status, String keyword, int limit) { return adminMapper.videos(status, keyword, limit); }
    public List<Map<String, Object>> audits(long videoId) { return adminMapper.audits(videoId); }
    @Transactional
    public void updateVideoStatus(long id, String status) {
        if (!VIDEO_STATUSES.contains(status)) throw new BusinessException("不支持的视频状态");
        if (adminMapper.updateVideoStatus(id, status) == 0) throw new BusinessException("视频不存在");
        evictRecommendCache();
        log.info("管理员更新视频状态: videoId={}, status={}", id, status);
    }

    /** 视频状态变化后清理用户端推荐缓存，避免审核通过的视频延迟最多 5 分钟才出现在列表。 */
    private void evictRecommendCache() {
        try {
            Set<String> keys = redisTemplate.keys("kilikili:recommend:*");
            if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        } catch (Exception exception) {
            log.warn("推荐缓存清理失败", exception);
        }
    }
}
