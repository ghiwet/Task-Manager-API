package com.example.taskmanager;

import com.example.taskmanager.dto.TaskCreateDto;
import com.example.taskmanager.dto.TaskDto;
import com.example.taskmanager.model.Task;
import com.example.taskmanager.repository.TaskRepository;
import com.example.taskmanager.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves tenant isolation is enforced by PostgreSQL row-level security, not just by the
 * application's owner filter. Unlike the other integration tests (which connect as the container
 * superuser and bypass RLS), this test connects the app as the non-owner "app_rls" role, so the
 * RLS policies actually apply. Several cases use the SAME owner across two tenants to show that
 * isolation holds even when the app-level owner check would have matched.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "rate-limit.enabled=false"
})
class TenantIsolationTest extends AbstractIntegrationTest {

    // Singleton container started manually (the testcontainers junit-jupiter extension is not a
    // dependency); reused for the JVM and reaped by Ryuk on shutdown.
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(TestcontainersConfig.POSTGRES_IMAGE);

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        // Migrations run as the container superuser, which creates the app_rls role and RLS policies.
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        // The app connects as the non-superuser app_rls role so RLS is actually enforced.
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "app_rls");
        registry.add("spring.datasource.password", () -> "app_rls_pass");
    }

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    @AfterEach
    void clean() {
        // deleteAll runs under RLS, so clear each tenant's rows separately.
        for (String tenant : List.of(TENANT_A, TENANT_B)) {
            TenantContext.setTenantId(tenant);
            try {
                taskRepository.deleteAll();
            } finally {
                TenantContext.clear();
            }
        }
    }

    private RequestPostProcessor user(String subject, String tenantId) {
        return jwt()
                .jwt(j -> j.subject(subject).claim("tenant_id", tenantId))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private Long createTask(String subject, String tenantId, String title) throws Exception {
        TaskCreateDto dto = new TaskCreateDto(title, "desc", false);
        MvcResult result = mockMvc.perform(post("/api/v1/tasks")
                        .with(user(subject, tenantId))
                        .contentType("application/json")
                        .content(jsonMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        return jsonMapper.readValue(result.getResponse().getContentAsString(), TaskDto.class).getId();
    }

    @Test
    void tenantOnlySeesItsOwnTasks() throws Exception {
        createTask("alice", TENANT_A, "tenant-a task");

        mockMvc.perform(get("/api/v1/tasks").with(user("bob", TENANT_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/tasks").with(user("alice", TENANT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void sameOwnerInDifferentTenantCannotReadTask() throws Exception {
        // Same subject "alice" in both tenants: the app's owner filter would match, only RLS blocks.
        Long id = createTask("alice", TENANT_A, "secret");

        mockMvc.perform(get("/api/v1/tasks/" + id).with(user("alice", TENANT_B)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/tasks").with(user("alice", TENANT_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void sameOwnerInDifferentTenantCannotModifyOrDelete() throws Exception {
        Long id = createTask("alice", TENANT_A, "secret");

        TaskCreateDto update = new TaskCreateDto("hacked", "x", true);
        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .with(user("alice", TENANT_B))
                        .contentType("application/json")
                        .content(jsonMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/tasks/" + id).with(user("alice", TENANT_B)))
                .andExpect(status().isNotFound());

        // Owner in the correct tenant can still delete it.
        mockMvc.perform(delete("/api/v1/tasks/" + id).with(user("alice", TENANT_A)))
                .andExpect(status().isNoContent());
    }

    @Test
    void repositoryFindAllIsScopedByRowLevelSecurity() {
        // A raw findAll() has no WHERE clause; isolation here can only come from the database.
        TenantContext.setTenantId(TENANT_A);
        try {
            Task task = new Task();
            task.setTitle("a-only");
            task.setOwner("alice");
            task.setTenantId(TENANT_A);
            taskRepository.save(task);
        } finally {
            TenantContext.clear();
        }

        TenantContext.setTenantId(TENANT_B);
        try {
            assertEquals(0, taskRepository.findAll().size(), "tenant-b must not see tenant-a rows");
        } finally {
            TenantContext.clear();
        }

        TenantContext.setTenantId(TENANT_A);
        try {
            assertTrue(taskRepository.findAll().stream().anyMatch(t -> t.getTitle().equals("a-only")));
        } finally {
            TenantContext.clear();
        }
    }
}
