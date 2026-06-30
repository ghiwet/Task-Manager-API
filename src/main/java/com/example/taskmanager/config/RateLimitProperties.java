package com.example.taskmanager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private int publicRequestsPerMinute = 20;
    private int authenticatedRequestsPerMinute = 60;
    private int registrationRequestsPerMinute = 5;
    private int assistantRequestsPerMinute = 10;
    private int bucketCleanupIntervalMinutes = 10;
}
