package com.kilikili.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.kilikili")
public class KilikiliWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(KilikiliWebApplication.class, args);
    }
}
