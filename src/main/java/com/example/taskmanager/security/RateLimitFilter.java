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

        String key;
        String keyType;
        int limit;

        if (authenticated && isAssistantEndpoint(request)) {
            // Separate, stricter bucket for the costly AI endpoint (own key so it doesn't share
            // the general per-user allowance).
            key = "assistant:" + auth.getName();
            keyType = "assistant";
            limit = properties.getAssistantRequestsPerMinute();
        } else if (authenticated) {
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

        ConsumptionProbe probe;
        try {
            probe = backend.tryConsume(key, limit);
        } catch (Exception ex) {
            // Fail open: don't block traffic if the rate-limit backend (Redis) is unavailable.
            log.warn("Rate limit backend unavailable ({}); allowing request", ex.getMessage());
            meterRegistry.counter("rate.limit.backend.error.total").increment();
            filterChain.doFilter(request, response);
            return;
        }

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
                && "/api/v1/users/register".equals(request.getRequestURI());
    }

    private boolean isAssistantEndpoint(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/v1/assistant/");
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

}
