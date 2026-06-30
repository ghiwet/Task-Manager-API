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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@EmbeddedKafka(partitions = 1)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "rate-limit.authenticated-requests-per-minute=3"
})
@DirtiesContext
class RateLimitFilterTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void whenRateLimitExceeded_thenReturns429WithRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/tasks")
                            .with(jwt().jwt(j -> j.subject("rate-test-user")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/tasks")
                        .with(jwt().jwt(j -> j.subject("rate-test-user")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-Rate-Limit-Remaining", "0"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Too Many Requests"));
    }

    @Test
    void whenWithinRateLimit_thenReturnsRemainingHeader() throws Exception {
        mockMvc.perform(get("/api/v1/tasks")
                        .with(jwt().jwt(j -> j.subject("header-test-user")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Rate-Limit-Remaining"));
    }
}
