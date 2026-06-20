package com.example.taskmanager.event;

import com.example.taskmanager.config.KafkaConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TaskEventDltConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskEventDltConsumer.class);

    @KafkaListener(
            topics = KafkaConfig.TASK_EVENTS_TOPIC + ".DLT",
            groupId = "${spring.kafka.consumer.group-id}-dlt"
    )
    public void handleDltEvent(ConsumerRecord<String, TaskEvent> record) {
        log.error("DLT - Failed to process task event: key={}, value={}, partition={}, offset={}",
                record.key(),
                record.value(),
                record.partition(),
                record.offset());
    }
}
