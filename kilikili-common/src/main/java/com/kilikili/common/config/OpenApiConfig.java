package com.kilikili.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI kilikiliOpenApi() {
        final String scheme = "BearerAuth";
        return new OpenAPI().info(new Info().title("KiliKili API 文档").version("v1.0.0")
                .description("仿 B 站视频社区后端接口。需登录的接口请在 Authorize 中填入 Bearer Token。"))
                .addSecurityItem(new SecurityRequirement().addList(scheme))
                .components(new Components().addSecuritySchemes(scheme,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("Token")));
    }
}
