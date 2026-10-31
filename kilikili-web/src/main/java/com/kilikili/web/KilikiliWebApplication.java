package com.kilikili.web;

import com.kilikili.web.ai.AiReviewProperties;
import com.kilikili.web.ai.vision.VisionProperties;
import com.kilikili.web.cache.CacheGovernanceProperties;
import com.kilikili.web.mq.MqProperties;
import com.kilikili.web.progress.PlayProgressProperties;
import com.kilikili.web.transcode.TranscodeProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.kilikili")
@MapperScan("com.kilikili")
@EnableScheduling
@EnableConfigurationProperties({TranscodeProperties.class, AiReviewProperties.class, PlayProgressProperties.class,
        CacheGovernanceProperties.class, MqProperties.class, VisionProperties.class})
public class KilikiliWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(KilikiliWebApplication.class, args);
    }
}
