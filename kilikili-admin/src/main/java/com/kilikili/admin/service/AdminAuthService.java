package com.kilikili.admin.service;

import com.kilikili.admin.dto.AdminLoginRequest;
import com.kilikili.admin.entity.AdminAccount;
import com.kilikili.admin.mapper.AdminMapper;
import com.kilikili.common.auth.AuthService;
import com.kilikili.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class AdminAuthService {
    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);
    private final AdminMapper adminMapper;
    private final AuthService authService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    public AdminAuthService(AdminMapper adminMapper, AuthService authService) { this.adminMapper = adminMapper; this.authService = authService; }
    public Map<String, Object> login(AdminLoginRequest request) {
        AdminAccount account = adminMapper.findAccountByUsername(request.username());
        if (account == null || !encoder.matches(request.password(), account.passwordHash())) throw new BusinessException("用户名或密码错误");
        if (!"ACTIVE".equals(account.status())) throw new BusinessException("该账号已被禁用");
        if (!"ADMIN".equals(account.role())) { log.warn("非管理员尝试登录管理端: userId={}", account.id()); throw new BusinessException("该账号没有管理权限"); }
        log.info("管理员登录成功: userId={}, username={}", account.id(), account.username());
        return Map.of("token", authService.createSession(account.id()), "user", Map.of("id", account.id(), "username", account.username(), "nickname", account.nickname(), "role", account.role()));
    }
    public AdminAccount requireAdmin(String authorization) {
        long id = authService.requireUserId(authorization);
        AdminAccount account = adminMapper.findAccountById(id);
        if (account == null || !"ACTIVE".equals(account.status()) || !"ADMIN".equals(account.role())) {
            log.warn("拒绝非管理员后台访问: userId={}", id);
            throw new BusinessException("没有管理权限");
        }
        return account;
    }

    public AdminAccount currentAdminOrNull(String authorization) {
        try {
            long id = authService.requireUserId(authorization);
            AdminAccount account = adminMapper.findAccountById(id);
            return account != null && "ACTIVE".equals(account.status()) && "ADMIN".equals(account.role()) ? account : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
