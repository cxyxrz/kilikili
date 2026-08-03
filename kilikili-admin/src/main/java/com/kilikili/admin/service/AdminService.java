package com.kilikili.admin.service;

import com.kilikili.common.exception.BusinessException;
import com.kilikili.admin.mapper.AdminMapper;
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
    private static final Set<String> USER_STATUSES = Set.of("ACTIVE", "DISABLED");
    private static final Set<String> VIDEO_STATUSES = Set.of("PENDING_REVIEW", "PUBLISHED", "REJECTED", "OFFLINE");
    private final AdminMapper adminMapper;
    public AdminService(AdminMapper adminMapper) { this.adminMapper = adminMapper; }
    public Map<String, Object> dashboard() { return adminMapper.dashboard(); }
    public List<Map<String, Object>> users(String keyword, int limit) { return adminMapper.users(keyword, limit); }
    public List<Map<String, Object>> videos(String status, String keyword, int limit) { return adminMapper.videos(status, keyword, limit); }
    @Transactional
    public void updateUserStatus(long id, String status) {
        if (!USER_STATUSES.contains(status)) throw new BusinessException("不支持的用户状态");
        if (adminMapper.updateUserStatus(id, status) == 0) throw new BusinessException("用户不存在");
        log.info("管理员更新用户状态: userId={}, status={}", id, status);
    }
    @Transactional
    public void updateVideoStatus(long id, String status) {
        if (!VIDEO_STATUSES.contains(status)) throw new BusinessException("不支持的视频状态");
        if (adminMapper.updateVideoStatus(id, status) == 0) throw new BusinessException("视频不存在");
        log.info("管理员更新视频状态: videoId={}, status={}", id, status);
    }
}
