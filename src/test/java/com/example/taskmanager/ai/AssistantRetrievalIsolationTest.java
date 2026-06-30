package com.example.taskmanager.ai;

import com.example.taskmanager.TestcontainersConfig;
import com.example.taskmanager.event.TaskEvent;
import com.example.taskmanager.event.TaskEventType;
import com.example.taskmanager.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the assistant's retrieval is scoped to the caller's tenant (RLS) AND owner (metadata
 * filter) — the "personal" model. Connects the app as the non-owner app_rls role so RLS applies,
 * and uses real local ONNX embeddings + pgvector. No chat model is configured: retrieval is the
 * security-critical part, so we assert on what is retrieved, not on a generated answer.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "rate-limit.enabled=false",
        "spring.ai.model.chat=none"
})
class AssistantRetrievalIsolationTest {

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
    private TaskEmbeddingService embeddingService;

    @Autowired
    private AssistantRetriever retriever;

    @Test
    void retrievalIsScopedToTenantAndOwner() {
        embeddingService.upsert(taskEvent(1L, "grocery list milk and eggs", "alice", "tenant-a"));
        embeddingService.upsert(taskEvent(2L, "grocery shopping for vegetables", "carol", "tenant-a"));
        embeddingService.upsert(taskEvent(3L, "grocery budget spreadsheet", "bob", "tenant-b"));

        // alice (tenant-a) sees only her own task — not carol's (same tenant, different owner).
        assertEquals(List.of(1L), taskIds(retrieveAs("tenant-a", "alice")));

        // owner filter within a tenant: carol sees only hers.
        assertEquals(List.of(2L), taskIds(retrieveAs("tenant-a", "carol")));

        // alice in tenant-b sees nothing — RLS blocks tenant-a rows even though the owner matches.
        assertTrue(retrieveAs("tenant-b", "alice").isEmpty());

        // bob (tenant-b) sees only his.
        assertEquals(List.of(3L), taskIds(retrieveAs("tenant-b", "bob")));
    }

    private List<Document> retrieveAs(String tenant, String owner) {
        TenantContext.setTenantId(tenant);
        try {
            return retriever.retrieve("groceries", owner, 10);
        } finally {
            TenantContext.clear();
        }
    }

    private List<Long> taskIds(List<Document> documents) {
        return documents.stream()
                .map(document -> ((Number) document.getMetadata().get("taskId")).longValue())
                .sorted()
                .toList();
    }

    private TaskEvent taskEvent(Long id, String title, String owner, String tenantId) {
        return TaskEvent.builder()
                .taskId(id)
                .title(title)
                .owner(owner)
                .tenantId(tenantId)
                .eventType(TaskEventType.CREATED)
                .build();
    }
}
