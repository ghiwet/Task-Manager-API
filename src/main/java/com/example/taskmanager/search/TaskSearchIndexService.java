package com.example.taskmanager.search;

import com.example.taskmanager.event.TaskEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    public TaskSearchIndexService(TaskSearchRepository repository) {
        this.repository = repository;
    }

    public void upsert(TaskEvent event) {
        try {
            repository.save(TaskDocument.builder()
                    .id(String.valueOf(event.getTaskId()))
                    .tenantId(event.getTenantId())
                    .owner(event.getOwner())
                    .title(event.getTitle())
                    .description(event.getDescription())
                    .completed(event.isCompleted())
                    .build());
        } catch (Exception ex) {
            log.warn("Failed to index task {} for search ({}); skipping", event.getTaskId(), ex.getMessage());
        }
    }

    public void delete(TaskEvent event) {
        try {
            repository.deleteById(String.valueOf(event.getTaskId()));
        } catch (Exception ex) {
            log.warn("Failed to remove task {} from search index ({}); skipping", event.getTaskId(), ex.getMessage());
        }
    }
}
