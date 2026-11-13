package com.example.taskmanager.search;

import com.example.taskmanager.AbstractIntegrationTest;
import com.example.taskmanager.TestcontainersConfig;
import com.example.taskmanager.event.TaskEvent;
import com.example.taskmanager.event.TaskEventType;
import com.example.taskmanager.model.Task;
import com.example.taskmanager.repository.TaskRepository;
import com.example.taskmanager.tenant.TenantContext;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies event-driven indexing: a CREATED task event lands in Elasticsearch, and a DELETED event
 * removes it. Also verifies a forged event (no backing row in Postgres) is dropped rather than
 * indexed, since the consumer reloads from the database instead of trusting the event payload.
 * Brings up its own Elasticsearch container so the rest of the suite stays lean.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EmbeddedKafka(partitions = 1)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "rate-limit.enabled=false"
})
class TaskSearchIndexTest extends AbstractIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class ElasticsearchTestConfig {
        @Bean
        @ServiceConnection
        ElasticsearchContainer elasticsearch() {
            return new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:9.4.2")
                    .withEnv("xpack.security.enabled", "false");
        }
    }

    @Autowired
    private TaskSearchIndexConsumer consumer;

    @Autowired
    private TaskSearchRepository repository;

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void indexesAndRemovesTasksFromEvents() {
        Long taskId = persistTask("buy milk", "tenant-a", "alice");

        consumer.onTaskEvent(record(event(TaskEventType.CREATED, taskId, "tenant-a", "alice")));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(repository.findById(String.valueOf(taskId))).isPresent());

        consumer.onTaskEvent(record(event(TaskEventType.DELETED, taskId, "tenant-a", "alice")));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(repository.findById(String.valueOf(taskId))).isEmpty());
    }

    @Test
    void forgedEventWithNoBackingRowIsNotIndexed() {
        // No Task row exists for this id -- a real event is only ever emitted alongside an actual
        // DB write (the outbox pattern), so this simulates a forged/stale event.
        long forgedTaskId = 999_999L;

        consumer.onTaskEvent(record(event(TaskEventType.CREATED, forgedTaskId, "tenant-a", "mallory")));

        // Give the (non-)write a moment, then assert it never lands.
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(repository.findById(String.valueOf(forgedTaskId))).isEmpty());
    }

    private Long persistTask(String title, String tenantId, String owner) {
        TenantContext.setTenantId(tenantId);
        try {
            Task task = new Task();
            task.setTitle(title);
            task.setDescription("from the store");
            task.setCompleted(false);
            task.setOwner(owner);
            task.setTenantId(tenantId);
            return taskRepository.save(task).getId();
        } finally {
            TenantContext.clear();
        }
    }

    private static TaskEvent event(TaskEventType type, Long taskId, String tenantId, String owner) {
        return TaskEvent.builder()
                .taskId(taskId)
                .title("buy milk")
                .description("from the store")
                .completed(false)
                .owner(owner)
                .tenantId(tenantId)
                .eventType(type)
                .timestamp("2026-07-05T10:00:00")
                .build();
    }

    private static ConsumerRecord<String, TaskEvent> record(TaskEvent event) {
        return new ConsumerRecord<>("task-events", 0, 0L, String.valueOf(event.getTaskId()), event);
    }
}
