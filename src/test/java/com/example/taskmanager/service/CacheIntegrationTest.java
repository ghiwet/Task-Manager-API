package com.example.taskmanager.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import com.example.taskmanager.tenant.TenantContext;
import com.example.taskmanager.AbstractIntegrationTest;
import com.example.taskmanager.TestcontainersConfig;
import com.example.taskmanager.config.CacheConfig;
import com.example.taskmanager.dto.TaskCreateDto;
import com.example.taskmanager.dto.TaskDto;
import com.example.taskmanager.exception.TaskNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private static final String TENANT = "tenant-test";
    private static final String OWNER = "cache-owner";

    @Autowired
    private TaskService taskService;

    @Autowired
    private CacheManager cacheManager;

    // Services read the tenant from TenantContext; with no tenant bound they would stamp the
    // '__unresolved__' sentinel, which V10 now forbids (threat #52). Bind a real one per test.
    @BeforeEach
    void bindTenant() {
        TenantContext.setTenantId(TENANT);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void readPopulatesCacheAndWriteEvicts() {
        TaskDto created = taskService.createTask(new TaskCreateDto("cached", "desc", false), OWNER);
        Long id = created.getId();
        Cache cache = cacheManager.getCache(CacheConfig.TASKS_CACHE);
        String key = TENANT + ":" + OWNER + ":" + id;

        taskService.findTask(id, OWNER);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(cache.get(key)).as("read populates the cache").isNotNull());

        taskService.updateTask(id, OWNER, new TaskCreateDto("updated", "desc", false));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(cache.get(key)).as("write evicts the cache entry").isNull());

        assertThat(taskService.findTask(id, OWNER).getTitle())
                .as("next read reflects the update").isEqualTo("updated");
    }

    // Threat #27: the entry lives under the owner's key, so an admin deleting someone else's task
    // must evict that key, not the admin's own. Previously the owner kept reading the deleted task.
    @WithMockUser(username = "cache-admin", roles = "ADMIN")
    @Test
    void adminDeleteEvictsTheOwnersEntry() {
        TaskDto created = taskService.createTask(new TaskCreateDto("owned", "desc", false), OWNER);
        Long id = created.getId();
        Cache cache = cacheManager.getCache(CacheConfig.TASKS_CACHE);
        String ownerKey = TENANT + ":" + OWNER + ":" + id;

        taskService.findTask(id, OWNER);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(cache.get(ownerKey)).as("owner's read is cached").isNotNull());

        taskService.deleteTask(id, "cache-admin", true);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(cache.get(ownerKey)).as("admin delete evicts the owner's entry").isNull());

        assertThatThrownBy(() -> taskService.findTask(id, OWNER))
                .as("owner no longer sees the deleted task")
                .isInstanceOf(TaskNotFoundException.class);
    }

    // The same owner name in another tenant must not hit this tenant's entry.
    @Test
    void cacheKeyIsScopedByTenant() {
        TaskDto created = taskService.createTask(new TaskCreateDto("tenant-scoped", "desc", false), OWNER);
        Long id = created.getId();
        Cache cache = cacheManager.getCache(CacheConfig.TASKS_CACHE);

        taskService.findTask(id, OWNER);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(cache.get(TENANT + ":" + OWNER + ":" + id)).isNotNull());

        TenantContext.setTenantId("tenant-other");
        assertThat(TaskService.cacheKey(OWNER, id)).isEqualTo("tenant-other:" + OWNER + ":" + id);
        assertThat(cache.get(TaskService.cacheKey(OWNER, id)))
                .as("another tenant's key for the same owner/id is a miss").isNull();
    }
}
