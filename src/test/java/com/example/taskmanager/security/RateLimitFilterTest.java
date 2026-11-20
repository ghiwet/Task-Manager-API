package com.example.taskmanager.security;

import com.example.taskmanager.AbstractIntegrationTest;
import com.example.taskmanager.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@EmbeddedKafka(partitions = 1)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "rate-limit.authenticated-requests-per-minute=3",
        "rate-limit.public-requests-per-minute=3"
})
@DirtiesContext
class RateLimitFilterTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void whenRateLimitExceeded_thenReturns429WithRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/tasks")
                            .with(jwt().jwt(j -> j.subject("rate-test-user").claim("preferred_username", "rate-test-user").claim("tenant_id", "tenant-test")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/tasks")
                        .with(jwt().jwt(j -> j.subject("rate-test-user").claim("preferred_username", "rate-test-user").claim("tenant_id", "tenant-test")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-Rate-Limit-Remaining", "0"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Too Many Requests"));
    }

    @Test
    void whenWithinRateLimit_thenReturnsRemainingHeader() throws Exception {
        mockMvc.perform(get("/api/v1/tasks")
                        .with(jwt().jwt(j -> j.subject("header-test-user").claim("preferred_username", "header-test-user").claim("tenant_id", "tenant-test")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Rate-Limit-Remaining"));
    }

    // Threat #16: anonymous callers are keyed by client IP. Rotating X-Forwarded-For must not buy a
    // fresh bucket per request; the filter keys on the resolved remote address only (in production
    // Tomcat's RemoteIpValve resolves it, trusting the header only from configured proxies).
    @Test
    void anonymousCallerCannotDodgeTheLimitByRotatingXForwardedFor() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/actuator/health")
                            .with(fromPeer("203.0.113.7"))
                            .header("X-Forwarded-For", randomIp()))
                    .andExpect(result -> assertNotEquals(429, result.getResponse().getStatus()));
        }

        mockMvc.perform(get("/actuator/health")
                        .with(fromPeer("203.0.113.7"))
                        .header("X-Forwarded-For", randomIp()))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void distinctAnonymousPeersGetSeparateBuckets() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/actuator/health").with(fromPeer("198.51.100.1")))
                    .andExpect(result -> assertNotEquals(429, result.getResponse().getStatus()));
        }
        mockMvc.perform(get("/actuator/health").with(fromPeer("198.51.100.2")))
                .andExpect(result -> assertNotEquals(429, result.getResponse().getStatus()));
    }

    // Threat #53: a throttled caller hitting many distinct paths must not create a meter per path.
    @Test
    void throttledRequestsToRandomPathsDoNotMintNewMeters() throws Exception {
        String peer = "192.0.2.77";
        // Other tests also throttle anonymous /actuator requests into the same series; measure the delta.
        double before = exceededCount("ip", "other");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/actuator/health").with(fromPeer(peer)));
        }
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/probe/" + UUID.randomUUID()).with(fromPeer(peer)))
                    .andExpect(status().isTooManyRequests());
        }

        assertEquals(0, meterRegistry.find("rate.limit.exceeded.total").tagKeys("path").counters().size(),
                "the raw URI must not be a tag");
        assertEquals(1, meterRegistry.find("rate.limit.exceeded.total").tags("key_type", "ip", "endpoint", "other")
                .counters().size(), "all random paths share one bounded series");
        assertEquals(5.0, exceededCount("ip", "other") - before, 0.0);
    }

    private double exceededCount(String keyType, String endpoint) {
        var counter = meterRegistry.find("rate.limit.exceeded.total").tags("key_type", keyType, "endpoint", endpoint).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private static RequestPostProcessor fromPeer(String remoteAddr) {
        return request -> {
            request.setRemoteAddr(remoteAddr);
            return request;
        };
    }

    private static String randomIp() {
        UUID u = UUID.randomUUID();
        return "10." + (u.hashCode() & 0xff) + "." + ((u.hashCode() >> 8) & 0xff) + "." + ((u.hashCode() >> 16) & 0xff);
    }
}
