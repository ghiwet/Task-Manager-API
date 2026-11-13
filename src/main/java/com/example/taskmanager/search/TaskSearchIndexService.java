package com.example.taskmanager.search;

import com.example.taskmanager.event.TaskEvent;
import com.example.taskmanager.model.Task;
import com.example.taskmanager.repository.TaskRepository;
import com.example.taskmanager.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Keeps the search index in sync with task events (upsert on create/update/complete, delete on
 * delete), keyed by task id so redelivery is idempotent. Indexing is best-effort: if Elasticsearch
 * is unavailable it logs and skips rather than failing the consumer, since the index can be rebuilt
 * from the source of truth (the database).
 */
@Service
public class TaskSearchIndexService {

    private static final Logger log = LoggerFactory.getLogger(TaskSearchIndexService.class);

    private final TaskSearchRepository repository;
    private final TaskRepository taskRepository;

    public TaskSearchIndexService(TaskSearchRepository repository, TaskRepository taskRepository) {
        this.repository = repository;
        this.taskRepository = taskRepository;
    }

    public void upsert(TaskEvent event) {
        try {
            // Reload from Postgres instead of trusting the event payload: a forged event can't plant
            // content into another tenant's index, since the tenant-scoped lookup only returns a row
            // that actually belongs to that tenant (RLS).
            Optional<Task> task = loadTask(event.getTaskId(), event.getTenantId());
            if (task.isEmpty()) {
                log.warn("Task {} not found for tenant {}; skipping search index upsert", event.getTaskId(), event.getTenantId());
                return;
            }
            Task t = task.get();
            repository.save(TaskDocument.builder()
                    .id(String.valueOf(t.getId()))
                    .tenantId(t.getTenantId())
                    .owner(t.getOwner())
                    .title(t.getTitle())
                    .description(t.getDescription())
                    .completed(t.isCompleted())
                    .build());
        } catch (Exception ex) {
            log.warn("Failed to index task {} for search ({}); skipping", event.getTaskId(), ex.getMessage());
        }
    }

    public void delete(TaskEvent event) {
        try {
            // Elasticsearch has no RLS, and the task row is already gone by the time a DELETED event
            // arrives, so a DB reload can't verify this one. Instead check the existing document's own
            // tenantId before deleting it, so a forged delete event can't remove another tenant's document.
            repository.findById(String.valueOf(event.getTaskId())).ifPresent(doc -> {
                if (doc.getTenantId() != null && doc.getTenantId().equals(event.getTenantId())) {
                    repository.deleteById(doc.getId());
                } else {
                    log.warn("Delete event for task {} claims tenant {} but the indexed document belongs to a different tenant; skipping",
                            event.getTaskId(), event.getTenantId());
                }
            });
        } catch (Exception ex) {
            log.warn("Failed to remove task {} from search index ({}); skipping", event.getTaskId(), ex.getMessage());
        }
    }

    private Optional<Task> loadTask(Long taskId, String tenantId) {
        TenantContext.setTenantId(tenantId);
        try {
            return taskRepository.findById(taskId);
        } finally {
            TenantContext.clear();
        }
    }
}
