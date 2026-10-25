package com.kilikili.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.kilikili")
@MapperScan("com.kilikili")
public class KilikiliAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(KilikiliAdminApplication.class, args);
    }
}