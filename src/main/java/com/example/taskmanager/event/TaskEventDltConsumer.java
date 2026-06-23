package com.example.taskmanager.event;

import com.example.taskmanager.config.KafkaConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TaskEventDltConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskEventDltConsumer.class);

    private final MeterRegistry meterRegistry;

    public TaskEventDltConsumer(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = KafkaConfig.TASK_EVENTS_TOPIC + ".DLT",
            groupId = "${spring.kafka.consumer.group-id}-dlt"
    )
    public void handleDltEvent(ConsumerRecord<String, TaskEvent> record) {
        meterRegistry.counter("kafka.events.dlt.total").increment();
        log.error("DLT - Failed to process task event: key={}, value={}, partition={}, offset={}",
                record.key(),
                record.value(),
                record.partition(),
                record.offset());
    }
}
