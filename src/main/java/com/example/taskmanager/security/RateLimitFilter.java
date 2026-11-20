package com.example.taskmanager.security;

import com.example.taskmanager.config.RateLimitProperties;
import io.github.bucket4j.ConsumptionProbe;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Bucket4j rate limiting, with buckets in Redis so limits are shared across replicas. Authenticated
 * callers are keyed per user (with a stricter bucket for the AI assistant and registration); anonymous
 * callers per client IP. Fails open if Redis is unavailable. Placed in the api and browser chains only.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitProperties properties;
    private final RateLimiterBackend backend;
    private final MeterRegistry meterRegistry;

    public RateLimitFilter(RateLimitProperties properties, RateLimiterBackend backend, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.backend = backend;
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
        String path = RequestPaths.withinApplication(request);

        String key;
        String keyType;
        int limit;

        if (authenticated && isAssistantEndpoint(path)) {
            key = "assistant:" + auth.getName(); // own bucket for the costly AI endpoint
            keyType = "assistant";
            limit = properties.getAssistantRequestsPerMinute();
        } else if (authenticated) {
            key = "user:" + auth.getName();
            keyType = "user";
            limit = isRegistrationEndpoint(request, path)
                    ? properties.getRegistrationRequestsPerMinute()
                    : properties.getAuthenticatedRequestsPerMinute();
        } else {
            key = "ip:" + resolveClientIp(request);
            keyType = "ip";
            limit = properties.getPublicRequestsPerMinute();
        }

        ConsumptionProbe probe;
        try {
            probe = backend.tryConsume(key, limit);
        } catch (Exception ex) {
            log.warn("Rate limit backend unavailable ({}); allowing request", ex.getMessage());
            meterRegistry.counter("rate.limit.backend.error.total").increment();
            filterChain.doFilter(request, response); // fail open
            return;
        }

        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000);
            meterRegistry.counter("rate.limit.exceeded.total",
                    "key_type", keyType, "endpoint", endpointGroup(request, path)).increment();

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

    /**
     * Bounded tag value for the exceeded counter. Never the raw URI: it's client-controlled, so a throttled
     * caller could mint a meter per random path (threat #53). The route template isn't known yet either,
     * since this filter runs before Spring MVC matches a handler.
     */
    private static String endpointGroup(HttpServletRequest request, String path) {
        if (isAssistantEndpoint(path)) {
            return "assistant";
        }
        if (isRegistrationEndpoint(request, path)) {
            return "register";
        }
        return path.startsWith("/api/") ? "api" : "other";
    }

    private static boolean isRegistrationEndpoint(HttpServletRequest request, String path) {
        return "POST".equalsIgnoreCase(request.getMethod()) && "/api/v1/users/register".equals(path);
    }

    private static boolean isAssistantEndpoint(String path) {
        return path.startsWith("/api/v1/assistant/");
    }

    /**
     * The client IP as resolved by Tomcat's RemoteIpValve ({@code server.forward-headers-strategy=native}),
     * which honours X-Forwarded-For only from configured trusted proxies. Never read the header here: it's
     * client-controlled, and rotating it would buy a fresh bucket per request (threat #16).
     */
    private static String resolveClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
