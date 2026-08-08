package com.kilikili.admin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminWebConfig implements WebMvcConfigurer {
    private final AdminOperationLogInterceptor adminOperationLogInterceptor;

    public AdminWebConfig(AdminOperationLogInterceptor adminOperationLogInterceptor) {
        this.adminOperationLogInterceptor = adminOperationLogInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminOperationLogInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/**");
    }
}
