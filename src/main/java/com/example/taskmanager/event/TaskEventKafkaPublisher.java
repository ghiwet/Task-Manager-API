package com.example.taskmanager.event;

import com.example.taskmanager.config.KafkaConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TaskEventKafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(TaskEventKafkaPublisher.class);

    private final KafkaTemplate<String, TaskEvent> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public TaskEventKafkaPublisher(KafkaTemplate<String, TaskEvent> kafkaTemplate, MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    @EventListener
    public void handleTaskEvent(TaskEvent event) {
        try {
            String key = String.valueOf(event.getTaskId());

            kafkaTemplate.send(KafkaConfig.TASK_EVENTS_TOPIC, key, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish task event [{}] for task [{}]: {}",
                                    event.getEventType(), event.getTaskId(), ex.getMessage(), ex);
                            meterRegistry.counter("kafka.events.failed.total", "type", event.getEventType().name()).increment();
                        } else {
                            log.info("Published task event [{}] for task [{}] to partition [{}] at offset [{}]",
                                    event.getEventType(),
                                    event.getTaskId(),
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                            meterRegistry.counter("kafka.events.published.total", "type", event.getEventType().name()).increment();
                        }
                    });
        } catch (Exception ex) {
            log.error("Failed to send task event [{}] for task [{}]: {}",
                    event.getEventType(), event.getTaskId(), ex.getMessage(), ex);
            meterRegistry.counter("kafka.events.failed.total", "type", event.getEventType().name()).increment();
        }
    }
}
