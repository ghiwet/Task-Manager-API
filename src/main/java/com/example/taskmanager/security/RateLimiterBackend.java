package com.example.taskmanager.security;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PreDestroy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed Bucket4j buckets, so the rate limit is shared across all app replicas (the previous
 * in-memory map enforced the limit per instance — N replicas meant N× the intended limit).
 *
 * The Redis connection is established lazily and rebuilt on demand, so the app boots even when Redis
 * is down; callers treat a thrown exception as "backend unavailable" and fail open. Bucket keys carry
 * a TTL (refill window) so stale entries expire on their own — no cleanup job needed.
 *
 * <p>The Redis address is read at first use, not at construction: this bean is created very early (via the
 * filter's servlet registration) on an embedded server, before late-registered properties such as a test
 * container's address exist.
 */
@Component
public class RateLimiterBackend {

    private final Environment environment;
    private volatile RedisClient redisClient;
    private volatile LettuceBasedProxyManager<String> proxyManager;

    public RateLimiterBackend(Environment environment) {
        this.environment = environment;
    }

    /** Consumes one token for {@code key}; throws if Redis is unreachable (caller fails open). */
    public ConsumptionProbe tryConsume(String key, int tokensPerMinute) {
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(tokensPerMinute).refillGreedy(tokensPerMinute, Duration.ofMinutes(1)))
                .build();
        return proxyManager().builder().build(key, configuration).tryConsumeAndReturnRemaining(1);
    }

    private LettuceBasedProxyManager<String> proxyManager() {
        LettuceBasedProxyManager<String> local = this.proxyManager;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (this.proxyManager == null) {
                if (this.redisClient == null) {
                    this.redisClient = RedisClient.create(redisUri());
                }
                StatefulRedisConnection<String, byte[]> connection =
                        redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
                this.proxyManager = LettuceBasedProxyManager.builderFor(connection)
                        .withExpirationStrategy(ExpirationAfterWriteStrategy
                                .basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
                        .build();
            }
            return this.proxyManager;
        }
    }

    private RedisURI redisUri() {
        RedisURI.Builder uri = RedisURI.builder()
                .withHost(environment.getProperty("spring.data.redis.host", "localhost"))
                .withPort(environment.getProperty("spring.data.redis.port", Integer.class, 6379))
                .withTimeout(Duration.ofSeconds(1)); // fast fail so a slow/down Redis adds no latency
        String password = environment.getProperty("spring.data.redis.password", "");
        if (!password.isBlank()) {
            uri.withPassword(password.toCharArray());
        }
        return uri.build();
    }

    @PreDestroy
    void shutdown() {
        RedisClient client = this.redisClient;
        if (client != null) {
            client.shutdown();
        }
    }
}
