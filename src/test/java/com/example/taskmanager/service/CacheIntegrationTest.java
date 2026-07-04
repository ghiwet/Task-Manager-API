package com.example.taskmanager.service;

import com.example.taskmanager.AbstractIntegrationTest;
import com.example.taskmanager.TestcontainersConfig;
import com.example.taskmanager.config.CacheConfig;
import com.example.taskmanager.dto.TaskCreateDto;
import com.example.taskmanager.dto.TaskDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies cache-aside for task reads: a read populates the Redis cache, a write evicts the entry,
 * and the next read reflects the new value. Cache visibility is polled (await) because the cache is
 * fail-open — a transient Redis hiccup surfaces as a momentary miss, which is acceptable by design.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EmbeddedKafka(partitions = 1)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "rate-limit.enabled=false"
})
class CacheIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER = "cache-owner";

    @Autowired
    private TaskService taskService;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void readPopulatesCacheAndWriteEvicts() {
        TaskDto created = taskService.createTask(new TaskCreateDto("cached", "desc", false), OWNER);
        Long id = created.getId();
        Cache cache = cacheManager.getCache(CacheConfig.TASKS_CACHE);
        String key = OWNER + ":" + id;

        taskService.findTask(id, OWNER);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(cache.get(key)).as("read populates the cache").isNotNull());

        taskService.updateTask(id, OWNER, new TaskCreateDto("updated", "desc", false));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(cache.get(key)).as("write evicts the cache entry").isNull());

        assertThat(taskService.findTask(id, OWNER).getTitle())
                .as("next read reflects the update").isEqualTo("updated");
    }
}
