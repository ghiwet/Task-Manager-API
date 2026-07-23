package com.example.taskmanager.search;

import com.example.taskmanager.AbstractIntegrationTest;
import com.example.taskmanager.TestcontainersConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies full-text search: matches are found and highlighted, and results are scoped to the
 * caller's owner and tenant (a user never sees another owner's/tenant's tasks).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EmbeddedKafka(partitions = 1)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "rate-limit.enabled=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureMockMvc
class TaskSearchTest extends AbstractIntegrationTest {

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
    private TaskSearchRepository repository;

    @Autowired
    private TaskSearchService searchService;

    @Autowired
    private MockMvc mockMvc;

    @BeforeAll
    void indexTasks() {
        repository.saveAll(List.of(
                doc("1", "alice", "tenant-a", "buy milk", "at the corner store"),
                doc("2", "alice", "tenant-a", "walk the dog", "in the park"),
                doc("3", "bob", "tenant-b", "buy milk", "different owner entirely")));
    }

    @Test
    void searchMatchesAndHighlights_scopedToOwnerAndTenant() {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            TaskSearchResponse resp = searchService.search("milk", null, "alice", "tenant-a", PageRequest.of(0, 10));
            // Only alice's matching task — not bob's "buy milk" (task 3), not the non-matching task 2.
            assertThat(resp.results()).extracting(TaskSearchResult::id).containsExactly(1L);
            assertThat(resp.total()).isEqualTo(1);
            // The matched term is highlighted.
            assertThat(resp.results().get(0).highlights()).anyMatch(h -> h.contains("<em>milk</em>"));
        });
    }

    @Test
    void emptyQueryReturnsAllOwnersTasks() {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            TaskSearchResponse resp = searchService.search(null, null, "alice", "tenant-a", PageRequest.of(0, 10));
            assertThat(resp.results()).extracting(TaskSearchResult::id).containsExactlyInAnyOrder(1L, 2L);
        });
    }

    @Test
    void searchEndpointRoutesAndScopesByJwt() {
        // GET /tasks/search must route to the search controller (not findTask/{id}); the tenant comes
        // from the JWT's tenant_id claim, the owner from its subject.
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/tasks/search").param("q", "milk")
                                .with(jwt().jwt(j -> j.subject("alice").claim("preferred_username", "alice").claim("tenant_id", "tenant-a"))
                                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.total").value(1))
                        .andExpect(jsonPath("$.results[0].id").value(1)));
    }

    @Test
    void highlightsAreHtmlEscaped() {
        // A task whose text contains HTML must come back escaped in the highlight, so the SPA can render
        // it without executing injected markup — only the <em> match tags stay live.
        repository.save(doc("99", "eve", "tenant-x", "<img src=x onerror=alert(1)> report", "n/a"));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            TaskSearchResponse resp = searchService.search("report", null, "eve", "tenant-x", PageRequest.of(0, 10));
            assertThat(resp.results()).hasSize(1);
            String hl = resp.results().get(0).highlights().get(0);
            assertThat(hl).contains("<em>report</em>");
            assertThat(hl).contains("&lt;img");
            assertThat(hl).doesNotContain("<img");
        });
    }

    @Test
    void adminSearchReturnsAllOwnersInTenant() {
        repository.save(doc("50", "carol", "tenant-a", "buy milk urgently", "carol's task"));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            // owner=null → across all owners in tenant-a (alice's id 1 + carol's id 50)...
            TaskSearchResponse resp = searchService.search("milk", null, null, "tenant-a", PageRequest.of(0, 10));
            assertThat(resp.results()).extracting(TaskSearchResult::id).contains(1L, 50L);
            // ...but still tenant-scoped: bob's tenant-b "buy milk" (id 3) is excluded.
            assertThat(resp.results()).extracting(TaskSearchResult::id).doesNotContain(3L);
        });
    }

    @Test
    void searchAllEndpointRequiresAdmin() {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/tasks/search/all").param("q", "milk")
                                .with(jwt().jwt(j -> j.subject("alice").claim("preferred_username", "alice").claim("tenant_id", "tenant-a"))
                                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                        .andExpect(status().isForbidden()));
    }

    @Test
    void prefixMatchFindsTasksSharingAStem() {
        repository.saveAll(List.of(
                doc("60", "dave", "tenant-c", "task1", "first"),
                doc("61", "dave", "tenant-c", "task2", "second")));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            // "task" is a prefix of the tokens "task1"/"task2" — bool_prefix matches both.
            TaskSearchResponse resp = searchService.search("task", null, "dave", "tenant-c", PageRequest.of(0, 10));
            assertThat(resp.results()).extracting(TaskSearchResult::id).containsExactlyInAnyOrder(60L, 61L);
        });
    }

    private static TaskDocument doc(String id, String owner, String tenant, String title, String description) {
        return TaskDocument.builder()
                .id(id).owner(owner).tenantId(tenant).title(title).description(description).completed(false).build();
    }
}
