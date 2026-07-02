package com.example.taskmanager.ai;

import com.example.taskmanager.TestcontainersConfig;
import com.example.taskmanager.dto.AssistantResponse;
import com.example.taskmanager.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the assistant degrades gracefully when the OpenAI chat call fails: instead of a 500,
 * the resilience fallback returns a degraded message alongside whatever tasks were retrieved.
 * The chat client points at an unreachable endpoint so the call fails locally (no external network),
 * exercising the real retry + fallback path.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "rate-limit.enabled=false",
        // AI on, but chat points nowhere so the call fails locally.
        "spring.ai.openai.api-key=test-key",
        "spring.ai.openai.base-url=http://localhost:1",
        "spring.ai.openai.chat.timeout=2s",
        // keep the test fast: short retry backoff.
        "resilience4j.retry.instances.openai.wait-duration=10ms",
        "resilience4j.retry.instances.openai.enable-exponential-backoff=false"
})
class AssistantResilienceTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(TestcontainersConfig.POSTGRES_IMAGE);

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "app_rls");
        registry.add("spring.datasource.password", () -> "app_rls_pass");
    }

    @Autowired
    private AssistantService assistantService;

    @Test
    void chatFailureDegradesGracefully() {
        TenantContext.setTenantId("tenant-a");
        try {
            AssistantResponse response = assistantService.ask("what are my tasks?", "alice");
            assertEquals(ResilientChatClient.UNAVAILABLE_MESSAGE, response.answer());
            assertTrue(response.retrievedTaskIds().isEmpty());
        } finally {
            TenantContext.clear();
        }
    }
}
