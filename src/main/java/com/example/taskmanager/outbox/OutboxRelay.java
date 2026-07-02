package com.example.taskmanager.outbox;

import com.example.taskmanager.event.TaskEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publishes staged outbox rows to Kafka. Runs on a schedule, claiming a batch of unpublished rows
 * with FOR UPDATE SKIP LOCKED (safe across instances), sending each and marking it published only
 * after Kafka acknowledges. A failed send leaves the row unpublished for the next poll, so an event
 * is never lost — at worst redelivered (consumers are idempotent). A second job prunes old rows.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, TaskEvent> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final MeterRegistry meterRegistry;

    public OutboxRelay(OutboxRepository outboxRepository, KafkaTemplate<String, TaskEvent> kafkaTemplate,
                       JsonMapper jsonMapper, MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.poll-interval-ms:2000}")
    @Transactional
    public void relay() {
        List<OutboxEvent> batch = outboxRepository.lockUnpublishedBatch(BATCH_SIZE);
        for (OutboxEvent row : batch) {
            try {
                TaskEvent event = jsonMapper.readValue(row.getPayload(), TaskEvent.class);
                kafkaTemplate.send(row.getTopic(), row.getAggregateId(), event).get(5, TimeUnit.SECONDS);
                row.setPublishedAt(Instant.now());
                outboxRepository.save(row);
                meterRegistry.counter("outbox.published.total", "type", row.getEventType()).increment();
            } catch (Exception ex) {
                // Likely the broker is unreachable — stop this batch so we don't hold the row locks
                // and connection through a timeout per row. Unpublished rows are retried next poll.
                log.error("Failed to relay outbox row {} ({}): {}", row.getId(), row.getEventType(), ex.getMessage());
                meterRegistry.counter("outbox.publish.failed.total", "type", row.getEventType()).increment();
                break;
            }
        }
    }

    @Scheduled(fixedDelayString = "${outbox.cleanup.interval-ms:3600000}")
    @Transactional
    public void cleanupPublished() {
        Instant cutoff = Instant.now().minus(72, ChronoUnit.HOURS);
        int deleted = outboxRepository.deletePublishedBefore(cutoff);
        if (deleted > 0) {
            log.info("Pruned {} published outbox rows older than {}", deleted, cutoff);
        }
    }
}
