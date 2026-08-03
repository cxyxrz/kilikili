package com.kilikili.admin.config;

import com.kilikili.admin.mapper.AdminMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class AdminBootstrapConfig {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapConfig.class);
    @Bean
    CommandLineRunner bootstrapAdmin(AdminMapper mapper,
                                     @Value("${ADMIN_BOOTSTRAP_USERNAME:}") String username,
                                     @Value("${ADMIN_BOOTSTRAP_PASSWORD:}") String password) {
        return args -> {
            if (username.isBlank() || password.isBlank()) return;
            if (mapper.findAccountByUsername(username) != null) {
                log.info("管理员初始化跳过：账号已存在: username={}", username);
                return;
            }
            mapper.insertBootstrapAdmin(username, new BCryptPasswordEncoder().encode(password), "系统管理员");
            log.info("管理员初始化完成: username={}", username);
        };
    }
}
