package com.example.taskmanager.ai;

import com.example.taskmanager.config.KafkaConfig;
import com.example.taskmanager.event.TaskEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Indexes task content into the vector store off the task event stream. Uses its own consumer
 * group so it receives every event independently of the notification consumer.
 */
@Component
public class TaskEmbeddingConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskEmbeddingConsumer.class);

    private final TaskEmbeddingService embeddingService;

    public TaskEmbeddingConsumer(TaskEmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @KafkaListener(topics = KafkaConfig.TASK_EVENTS_TOPIC, groupId = "task-embedding-group")
    public void onTaskEvent(ConsumerRecord<String, TaskEvent> record) {
        TaskEvent event = record.value();
        if (event.getTenantId() == null || event.getTenantId().isBlank()) {
            return; // can't tenant-scope an embedding without a tenant
        }
        try {
            switch (event.getEventType()) {
                case CREATED, UPDATED, COMPLETED -> embeddingService.upsert(event);
                case DELETED -> embeddingService.delete(event);
            }
        } catch (Exception ex) {
            // Fail open: a pgvector blip or bad event skips instead of stalling the partition.
            // Caught outside the service's @Transactional so the tx rolls back.
            log.warn("Failed to sync embedding for task {} ({}); skipping", event.getTaskId(), ex.getMessage());
        }
    }
}
