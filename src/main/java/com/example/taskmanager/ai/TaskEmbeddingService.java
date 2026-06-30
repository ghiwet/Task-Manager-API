package com.example.taskmanager.ai;

import com.example.taskmanager.event.TaskEvent;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the pgvector store in sync with task content. Each operation runs in a transaction that
 * first pins the tenant via {@code SET LOCAL app.current_tenant}, so the RLS-protected vector_store
 * sees the right tenant for both the insert's tenant_id default and the policy check. SET LOCAL is
 * transaction-scoped and resets on commit, so nothing leaks to the next pooled connection user.
 *
 * <p>The {@link VectorStore} is optional (absent when AI autoconfig is disabled, e.g. in tests),
 * in which case these operations are no-ops.
 */
@Service
public class TaskEmbeddingService {

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final JdbcTemplate jdbcTemplate;

    public TaskEmbeddingService(ObjectProvider<VectorStore> vectorStoreProvider, JdbcTemplate jdbcTemplate) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void upsert(TaskEvent event) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            return;
        }
        bindTenant(event.getTenantId());
        Document document = Document.builder()
                .id(documentId(event.getTaskId()))
                .text(buildContent(event))
                .metadata(Map.of(
                        "taskId", event.getTaskId(),
                        "owner", event.getOwner()))
                .build();
        vectorStore.add(List.of(document));
    }

    @Transactional
    public void delete(TaskEvent event) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            return;
        }
        bindTenant(event.getTenantId());
        vectorStore.delete(List.of(documentId(event.getTaskId())));
    }

    private void bindTenant(String tenantId) {
        jdbcTemplate.queryForObject("SELECT set_config('app.current_tenant', ?, true)", String.class, tenantId);
    }

    private String buildContent(TaskEvent event) {
        String description = event.getDescription();
        return (description == null || description.isBlank())
                ? event.getTitle()
                : event.getTitle() + "\n" + description;
    }

    // Stable per-task UUID so re-indexing a task upserts its row rather than duplicating it.
    private String documentId(Long taskId) {
        return UUID.nameUUIDFromBytes(("task:" + taskId).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
