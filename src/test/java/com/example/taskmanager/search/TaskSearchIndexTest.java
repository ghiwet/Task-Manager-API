package com.example.taskmanager.search;

import com.example.taskmanager.AbstractIntegrationTest;
import com.example.taskmanager.TestcontainersConfig;
import com.example.taskmanager.event.TaskEvent;
import com.example.taskmanager.event.TaskEventType;
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
 * removes it. Brings up its own Elasticsearch container so the rest of the suite stays lean.
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

    @Test
    void indexesAndRemovesTasksFromEvents() {
        consumer.onTaskEvent(record(event(TaskEventType.CREATED, "buy milk")));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(repository.findById("1")).isPresent());

        consumer.onTaskEvent(record(event(TaskEventType.DELETED, "buy milk")));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(repository.findById("1")).isEmpty());
    }

    private static TaskEvent event(TaskEventType type, String title) {
        return TaskEvent.builder()
                .taskId(1L)
                .title(title)
                .description("from the store")
                .completed(false)
                .owner("alice")
                .tenantId("tenant-a")
                .eventType(type)
                .timestamp("2026-07-05T10:00:00")
                .build();
    }

    private static ConsumerRecord<String, TaskEvent> record(TaskEvent event) {
        return new ConsumerRecord<>("task-events", 0, 0L, "1", event);
    }
}
