package com.kilikili.web.service;

import com.kilikili.common.auth.AuthService;
import com.kilikili.common.exception.BusinessException;
import com.kilikili.web.dto.LoginRequest;
import com.kilikili.web.dto.RegisterRequest;
import com.kilikili.web.dto.ProfileUpdateRequest;
import com.kilikili.web.entity.Account;
import com.kilikili.web.entity.AccountProfile;
import com.kilikili.web.mapper.AccountMapper;
import com.kilikili.common.storage.StorageService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private final AccountMapper accountMapper;
    private final AuthService authService;
    private final StorageService storageService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    public AccountService(AccountMapper accountMapper, AuthService authService, StorageService storageService) { this.accountMapper = accountMapper; this.authService = authService; this.storageService = storageService; }

    @Transactional
    public Map<String, Object> register(RegisterRequest request) {
        if (accountMapper.findByUsername(request.username()) != null) {
            log.warn("注册失败，用户名已存在: username={}", request.username());
            throw new BusinessException("用户名已存在");
        }
        accountMapper.insert(new Account(null, request.username(), encoder.encode(request.password()), request.nickname(), null, null, "ACTIVE"));
        Account account = accountMapper.findByUsername(request.username());
        log.info("用户注册成功: userId={}, username={}", account.id(), account.username());
        return session(account);
    }
    public Map<String, Object> login(LoginRequest request) {
        Account account = accountMapper.findByUsername(request.username());
        if (account == null || !encoder.matches(request.password(), account.passwordHash())) {
            log.warn("登录失败: username={}", request.username());
            throw new BusinessException("用户名或密码错误");
        }
        if (!"ACTIVE".equals(account.status())) {
            log.warn("已禁用用户尝试登录: userId={}", account.id());
            throw new BusinessException("该账号已被禁用");
        }
        log.info("用户登录成功: userId={}, username={}", account.id(), account.username());
        return session(account);
    }
    public Account current(long id) { return accountMapper.findById(id); }
    public AccountProfile profile(long id) {
        AccountProfile profile = accountMapper.findProfileById(id);
        if (profile == null) throw new BusinessException("用户不存在");
        String avatarUrl = profile.avatar() == null || profile.avatar().isBlank() ? null : storageService.presignedReadUrl(profile.avatar());
        return new AccountProfile(profile.id(), profile.username(), profile.nickname(), avatarUrl, profile.gender(), profile.birthday(), profile.bio());
    }
    @Transactional
    public AccountProfile updateProfile(long id, ProfileUpdateRequest request) {
        if (accountMapper.updateProfile(id, request.nickname(), request.gender(), request.birthday(), request.bio()) == 0) throw new BusinessException("用户不存在");
        log.info("用户资料已更新: userId={}", id);
        return profile(id);
    }
    @Transactional
    public AccountProfile uploadAvatar(long id, MultipartFile file) {
        String objectKey = storageService.uploadAvatar(file, id);
        if (accountMapper.updateAvatar(id, objectKey) == 0) throw new BusinessException("用户不存在");
        log.info("用户头像已更新: userId={}", id);
        return profile(id);
    }
    private Map<String, Object> session(Account account) { return Map.of("token", authService.createSession(account.id()), "user", Map.of("id", account.id(), "username", account.username(), "nickname", account.nickname())); }
}
