package com.kilikili.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.kilikili")
public class KilikiliAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(KilikiliAdminApplication.class, args);
    }
}
