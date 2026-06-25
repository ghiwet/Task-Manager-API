package com.example.taskmanager.security;

import com.example.taskmanager.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, BucketEntry> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal());

        String key;
        String keyType;
        int limit;

        if (authenticated) {
            key = "user:" + auth.getName();
            keyType = "user";
            limit = isRegistrationEndpoint(request)
                    ? properties.getRegistrationRequestsPerMinute()
                    : properties.getAuthenticatedRequestsPerMinute();
        } else {
            key = "ip:" + resolveClientIp(request);
            keyType = "ip";
            limit = properties.getPublicRequestsPerMinute();
        }

        BucketEntry entry = buckets.computeIfAbsent(key, k -> new BucketEntry(createBucket(limit)));
        entry.lastAccessedAt.set(System.currentTimeMillis());

        ConsumptionProbe probe = entry.bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000);
            meterRegistry.counter("rate.limit.exceeded.total", "key_type", keyType, "path", request.getRequestURI()).increment();

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setHeader("X-Rate-Limit-Remaining", "0");
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write(
                    "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Rate limit exceeded. Try again in "
                            + retryAfterSeconds + " seconds.\"}"
            );
        }
    }

    private boolean isRegistrationEndpoint(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/api/users/register".equals(request.getRequestURI());
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Bucket createBucket(int tokensPerMinute) {
        return Bucket.builder()
                .addLimit(Bandwidth.simple(tokensPerMinute, Duration.ofMinutes(1)))
                .build();
    }

    // Production would use bucket4j-redis for distributed rate limiting across multiple instances
    @Scheduled(fixedRateString = "${rate-limit.bucket-cleanup-interval-minutes:10}000")
    public void cleanupStaleBuckets() {
        long threshold = System.currentTimeMillis() - Duration.ofMinutes(properties.getBucketCleanupIntervalMinutes() * 2L).toMillis();
        buckets.entrySet().removeIf(entry -> entry.getValue().lastAccessedAt.get() < threshold);
    }

    private static class BucketEntry {
        final Bucket bucket;
        final AtomicLong lastAccessedAt;

        BucketEntry(Bucket bucket) {
            this.bucket = bucket;
            this.lastAccessedAt = new AtomicLong(System.currentTimeMillis());
        }
    }
}
