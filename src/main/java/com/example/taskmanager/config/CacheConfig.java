package com.example.taskmanager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Duration;

/**
 * Redis-backed cache-aside for task reads. A RedisCacheConfiguration bean sets the default TTL that
 * Spring Boot's auto-configured RedisCacheManager applies. The error handler makes the cache fail
 * open: if Redis is unavailable, cache reads/writes are logged and skipped so the request falls
 * through to the database instead of failing.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String TASKS_CACHE = "tasks";

    @Bean
    RedisCacheConfiguration redisCacheConfiguration(@Value("${cache.tasks.ttl-minutes:10}") long ttlMinutes) {
        return RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(ttlMinutes));
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
