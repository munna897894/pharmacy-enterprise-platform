package com.jagapathi.pharmacy.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class AuditServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}
