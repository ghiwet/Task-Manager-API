package com.example.taskmanager.search;

import com.example.taskmanager.config.KafkaConfig;
import com.example.taskmanager.event.TaskEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Indexes task content for search off the task event stream, on its own consumer group so it
 * receives every event independently of the other consumers. Mirrors TaskEmbeddingConsumer.
 */
@Component
public class TaskSearchIndexConsumer {

    private final TaskSearchIndexService indexService;

    public TaskSearchIndexConsumer(TaskSearchIndexService indexService) {
        this.indexService = indexService;
    }

    @KafkaListener(topics = KafkaConfig.TASK_EVENTS_TOPIC, groupId = "task-search-index-group")
    public void onTaskEvent(ConsumerRecord<String, TaskEvent> record) {
        TaskEvent event = record.value();
        if (event.getTenantId() == null || event.getTenantId().isBlank()) {
            return; // can't tenant-scope a search document without a tenant
        }
        switch (event.getEventType()) {
            case CREATED, UPDATED, COMPLETED -> indexService.upsert(event);
            case DELETED -> indexService.delete(event);
        }
    }
}
