package com.example.taskmanager.event;

import com.example.taskmanager.config.KafkaConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TaskNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskNotificationConsumer.class);

    private final MeterRegistry meterRegistry;

    public TaskNotificationConsumer(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = KafkaConfig.TASK_EVENTS_TOPIC,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handleTaskEvent(ConsumerRecord<String, TaskEvent> record) {
        TaskEvent event = record.value();
        meterRegistry.counter("kafka.events.consumed.total", "type", event.getEventType().name()).increment();

        log.info("Received task event: type={}, taskId={}, title='{}', partition={}, offset={}",
                event.getEventType(),
                event.getTaskId(),
                event.getTitle(),
                record.partition(),
                record.offset());

        switch (event.getEventType()) {
            case CREATED -> log.info("[NOTIFICATION] Task '{}' (ID: {}) was created",
                    event.getTitle(), event.getTaskId());
            case UPDATED -> log.info("[NOTIFICATION] Task '{}' (ID: {}) was updated",
                    event.getTitle(), event.getTaskId());
            case COMPLETED -> log.info("[NOTIFICATION] Task '{}' (ID: {}) has been completed!",
                    event.getTitle(), event.getTaskId());
            case DELETED -> log.info("[NOTIFICATION] Task '{}' (ID: {}) was deleted",
                    event.getTitle(), event.getTaskId());
        }
    }
}
