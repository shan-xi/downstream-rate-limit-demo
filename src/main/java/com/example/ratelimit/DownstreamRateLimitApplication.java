package com.example.ratelimit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DownstreamRateLimitApplication {
    public static void main(String[] args) {
        SpringApplication.run(DownstreamRateLimitApplication.class, args);
    }
}
