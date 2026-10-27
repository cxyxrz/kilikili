package com.kilikili.web.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 未配置 DEEPSEEK_API_KEY 时关闭 Spring AI 的模型自动装配，避免缺少 api-key 导致启动失败；
 * 配置了 key 时保持 Spring AI starter 的默认行为。
 */
public class AiAvailabilityEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (StringUtils.hasText(environment.getProperty("spring.ai.openai.api-key"))) return;
        Map<String, Object> disabled = new LinkedHashMap<>();
        disabled.put("spring.ai.model.chat", "none");
        disabled.put("spring.ai.model.embedding", "none");
        disabled.put("spring.ai.model.image", "none");
        disabled.put("spring.ai.model.audio.speech", "none");
        disabled.put("spring.ai.model.audio.transcription", "none");
        disabled.put("spring.ai.model.moderation", "none");
        disabled.put("spring.ai.chat.client.enabled", "false");
        environment.getPropertySources().addFirst(new MapPropertySource("kilikili-ai-disabled", disabled));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
