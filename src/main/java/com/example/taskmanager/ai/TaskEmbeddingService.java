package com.example.taskmanager.ai;

import com.example.taskmanager.event.TaskEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(TaskEmbeddingService.class);

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
        // Reload from Postgres on the same tenant-bound connection rather than trusting the event
        // payload: a forged Kafka event can't plant arbitrary content since RLS only returns the
        // row if it actually belongs to the tenant we just bound.
        TaskRow row = loadTask(event.getTaskId());
        if (row == null) {
            log.warn("Task {} not found for tenant {}; skipping embedding upsert", event.getTaskId(), event.getTenantId());
            return;
        }
        Document document = Document.builder()
                .id(documentId(event.getTaskId()))
                .text(buildContent(row.title(), row.description()))
                .metadata(Map.of(
                        "taskId", event.getTaskId(),
                        "owner", row.owner()))
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

    private TaskRow loadTask(Long taskId) {
        List<TaskRow> rows = jdbcTemplate.query(
                "SELECT title, description, owner FROM tasks WHERE id = ?",
                (rs, rowNum) -> new TaskRow(rs.getString("title"), rs.getString("description"), rs.getString("owner")),
                taskId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String buildContent(String title, String description) {
        return (description == null || description.isBlank()) ? title : title + "\n" + description;
    }

    // Stable per-task UUID so re-indexing a task upserts its row rather than duplicating it.
    private String documentId(Long taskId) {
        return UUID.nameUUIDFromBytes(("task:" + taskId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private record TaskRow(String title, String description, String owner) {}
}
