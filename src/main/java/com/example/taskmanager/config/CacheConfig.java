package com.example.taskmanager.config;

import com.example.taskmanager.dto.TaskDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

/**
 * Redis-backed cache-aside for task reads, failing open: Redis errors are logged and the request
 * falls through to the database.
 *
 * <p>Values are JSON bound to {@link TaskDto}, never JDK serialization (threat #28). The serializer
 * carries no type information, so a value planted in Redis can only ever become a {@code TaskDto}
 * (or fail to parse and be treated as a miss), not an arbitrary gadget class. Only declared caches
 * exist, so a new cache can't silently fall back to the JDK serializer default.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String TASKS_CACHE = "tasks";

    @Bean
    RedisCacheManagerBuilderCustomizer tasksCache(@Value("${cache.tasks.ttl-minutes:10}") long ttlMinutes) {
        RedisCacheConfiguration tasks = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(ttlMinutes))
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(TaskDto.class)));
        return builder -> builder.disableCreateOnMissingCache().withCacheConfiguration(TASKS_CACHE, tasks);
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
