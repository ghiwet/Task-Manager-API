package com.example.taskmanager.audit;

import com.example.taskmanager.AbstractIntegrationTest;
import com.example.taskmanager.TestcontainersConfig;
import com.example.taskmanager.dto.TaskCreateDto;
import com.example.taskmanager.dto.TaskDto;
import com.example.taskmanager.enumration.Role;
import com.example.taskmanager.model.AppUser;
import com.example.taskmanager.repository.AppUserRepository;
import com.example.taskmanager.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Proves the audit trail (threat #2): admin cross-user actions create the right row, and --
// connecting as app_rls, like TenantIsolationTest -- that app_rls genuinely cannot UPDATE/DELETE it.
@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "rate-limit.enabled=false"
})
class AuditLogTest extends AbstractIntegrationTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(TestcontainersConfig.POSTGRES_IMAGE);

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "app_rls");
        registry.add("spring.datasource.password", () -> "app_rls_pass");
    }

    private static final String TENANT_A = "tenant-a";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RequestPostProcessor admin(String subject) {
        return jwt()
                .jwt(j -> j.subject(subject).claim("preferred_username", subject).claim("tenant_id", TENANT_A))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private RequestPostProcessor user(String subject) {
        return jwt()
                .jwt(j -> j.subject(subject).claim("preferred_username", subject).claim("tenant_id", TENANT_A))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private void persistUser(String username) {
        TenantContext.setTenantId(TENANT_A);
        try {
            AppUser newUser = new AppUser();
            newUser.setUsername(username);
            newUser.setPassword(passwordEncoder.encode("password123!A"));
            newUser.setTenantId(TENANT_A);
            newUser.setRoles(Set.of(Role.ROLE_USER));
            appUserRepository.save(newUser);
        } finally {
            TenantContext.clear();
        }
    }

    private AuditLog latestEntry() {
        TenantContext.setTenantId(TENANT_A);
        try {
            List<AuditLog> entries = auditLogRepository.findAllByOrderByCreatedAtDesc(
                    org.springframework.data.domain.Pageable.ofSize(1)).getContent();
            assertTrue(!entries.isEmpty(), "expected an audit row to exist");
            return entries.get(0);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void adminDeletingAnotherUsersTaskIsAudited() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/tasks")
                        .with(user("alice"))
                        .contentType("application/json")
                        .content(jsonMapper.writeValueAsString(new TaskCreateDto("alice's task", "desc", false))))
                .andExpect(status().isCreated())
                .andReturn();
        Long taskId = jsonMapper.readValue(created.getResponse().getContentAsString(), TaskDto.class).getId();

        mockMvc.perform(delete("/api/v1/tasks/" + taskId).with(admin("root-admin")))
                .andExpect(status().isNoContent());

        AuditLog entry = latestEntry();
        assertEquals("root-admin", entry.getActor());
        assertEquals(AuditAction.TASK_DELETED, entry.getAction());
        assertEquals(String.valueOf(taskId), entry.getTargetId());
    }

    @Test
    void adminDeletingOwnTaskIsNotAudited() throws Exception {
        long before = countEntries();
        MvcResult created = mockMvc.perform(post("/api/v1/tasks")
                        .with(admin("root-admin"))
                        .contentType("application/json")
                        .content(jsonMapper.writeValueAsString(new TaskCreateDto("admin's own task", "desc", false))))
                .andExpect(status().isCreated())
                .andReturn();
        Long taskId = jsonMapper.readValue(created.getResponse().getContentAsString(), TaskDto.class).getId();

        mockMvc.perform(delete("/api/v1/tasks/" + taskId).with(admin("root-admin")))
                .andExpect(status().isNoContent());

        assertEquals(before, countEntries(), "an admin managing their own task shouldn't be audited");
    }

    @Test
    void adminResettingAnotherUsersPasswordIsAudited() throws Exception {
        persistUser("bob");

        mockMvc.perform(put("/api/v1/users/bob")
                        .with(admin("root-admin"))
                        .contentType("application/json")
                        .content("{\"password\":\"newPassword123!A\"}"))
                .andExpect(status().isOk());

        AuditLog entry = latestEntry();
        assertEquals("root-admin", entry.getActor());
        assertEquals(AuditAction.PASSWORD_RESET, entry.getAction());
        assertEquals("bob", entry.getTargetId());
    }

    @Test
    void adminDeletingAnotherUserIsAudited() throws Exception {
        persistUser("carol");

        mockMvc.perform(delete("/api/v1/users/carol").with(admin("root-admin")))
                .andExpect(status().isNoContent());

        AuditLog entry = latestEntry();
        assertEquals("root-admin", entry.getActor());
        assertEquals(AuditAction.USER_DELETED, entry.getAction());
        assertEquals("carol", entry.getTargetId());
    }

    @Test
    void appRlsCannotUpdateOrDeleteAuditLogRows() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/tasks")
                        .with(user("dave"))
                        .contentType("application/json")
                        .content(jsonMapper.writeValueAsString(new TaskCreateDto("dave's task", "desc", false))))
                .andExpect(status().isCreated())
                .andReturn();
        Long taskId = jsonMapper.readValue(created.getResponse().getContentAsString(), TaskDto.class).getId();
        mockMvc.perform(delete("/api/v1/tasks/" + taskId).with(admin("root-admin")))
                .andExpect(status().isNoContent());

        Long entryId = latestEntry().getId();

        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("UPDATE audit_log SET actor = ? WHERE id = ?", "tampered", entryId));
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("DELETE FROM audit_log WHERE id = ?", entryId));
    }

    @Test
    void adminCanListAuditLog() throws Exception {
        persistUser("erin");
        mockMvc.perform(delete("/api/v1/users/erin").with(admin("root-admin")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/audit").with(admin("root-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].actor").value("root-admin"))
                .andExpect(jsonPath("$.content[0].action").value("USER_DELETED"))
                .andExpect(jsonPath("$.content[0].targetId").value("erin"));
    }

    @Test
    void nonAdminCannotListAuditLog() throws Exception {
        mockMvc.perform(get("/api/v1/audit").with(user("frank")))
                .andExpect(status().isForbidden());
    }

    private long countEntries() {
        TenantContext.setTenantId(TENANT_A);
        try {
            return auditLogRepository.count();
        } finally {
            TenantContext.clear();
        }
    }
}
