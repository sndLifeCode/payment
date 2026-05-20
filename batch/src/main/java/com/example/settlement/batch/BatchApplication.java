package com.example.settlement.batch;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.example.settlement")
@EnableJpaRepositories(basePackages = "com.example.settlement.core.persistence.repository")
@EntityScan(basePackages = "com.example.settlement.core.persistence.entity")
public class BatchApplication {
    public static void main(String[] args) {
        SpringApplication.run(BatchApplication.class, args);
    }
}
