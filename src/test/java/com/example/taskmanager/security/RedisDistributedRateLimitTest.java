package com.example.taskmanager.security;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the rate limit is shared across instances via Redis: two independent RateLimiterBackends
 * (each standing in for an app replica) enforce a single limit. With the old in-memory buckets, each
 * instance had its own allowance, so N replicas allowed N× the limit — this is the bug being fixed.
 */
class RedisDistributedRateLimitTest {

    private static GenericContainer<?> redis;

    @BeforeAll
    static void startRedis() {
        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        redis.start();
    }

    @AfterAll
    static void stopRedis() {
        if (redis != null) {
            redis.stop();
        }
    }

    @Test
    void limitIsSharedAcrossInstances() {
        String host = redis.getHost();
        int port = redis.getMappedPort(6379);
        RateLimiterBackend instanceA = new RateLimiterBackend(host, port, "");
        RateLimiterBackend instanceB = new RateLimiterBackend(host, port, "");
        try {
            String key = "user:shared-across-instances";
            int limit = 5;

            // 3 requests on instance A + 2 on instance B = 5 total, all within the shared limit.
            for (int i = 0; i < 3; i++) {
                assertThat(instanceA.tryConsume(key, limit).isConsumed()).isTrue();
            }
            for (int i = 0; i < 2; i++) {
                assertThat(instanceB.tryConsume(key, limit).isConsumed()).isTrue();
            }

            // The 6th request on either instance is rejected — the bucket in Redis is shared and empty.
            assertThat(instanceB.tryConsume(key, limit).isConsumed()).isFalse();
            assertThat(instanceA.tryConsume(key, limit).isConsumed()).isFalse();
        } finally {
            instanceA.shutdown();
            instanceB.shutdown();
        }
    }
}
