package com.example.taskmanager;

import com.example.taskmanager.dto.TaskCreateDto;
import com.example.taskmanager.dto.TaskDto;
import com.example.taskmanager.repository.TaskRepository;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@EmbeddedKafka(partitions = 1)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "rate-limit.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TaskControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private TaskRepository taskRepository;

    private Long id;

    @BeforeAll
    void setup() {
        taskRepository.deleteAll();
    }

    @Test
    @Order(1)
    void testCreateTask() throws Exception {
        TaskCreateDto taskCreateDto = new TaskCreateDto("Test Title", "Test Description", true);

        mockMvc.perform(post("/api/tasks")
                        .with(jwt().jwt(j -> j.subject("user1")).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(taskCreateDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Test Title"))
                .andExpect(jsonPath("$.owner").value("user1"));
    }

    @Test
    @Order(2)
    void testGetTask() throws Exception {
        TaskCreateDto createDto = new TaskCreateDto("Fetch Title", "Fetch Description", false);

        MvcResult createResult = mockMvc.perform(post("/api/tasks")
                        .with(jwt().jwt(j -> j.subject("user1")).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andReturn();

        TaskDto createdTask = jsonMapper.readValue(createResult.getResponse().getContentAsString(), TaskDto.class);
        id = createdTask.getId();

        MvcResult result = mockMvc.perform(get("/api/tasks/" + id)
                        .with(jwt().jwt(j -> j.subject("user1")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andReturn();

        TaskDto responseTask = jsonMapper.readValue(result.getResponse().getContentAsString(), TaskDto.class);

        assertNotNull(responseTask.getId());
        assertEquals("Fetch Title", responseTask.getTitle());
        assertEquals("user1", responseTask.getOwner());
    }

    @Test
    @Order(3)
    void testUpdateTask() throws Exception {
        TaskCreateDto taskCreateDto = new TaskCreateDto("Modified Title", "Modified Description", true);

        MvcResult result = mockMvc
                .perform(put("/api/tasks/" + id)
                        .with(jwt().jwt(j -> j.subject("user1")).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(taskCreateDto)))
                .andExpect(status().isOk())
                .andReturn();

        TaskDto responseTask = jsonMapper.readValue(result.getResponse().getContentAsString(), TaskDto.class);

        assertEquals(responseTask.getId(), id);
        assertEquals("Modified Title", responseTask.getTitle());
    }

    @Test
    @Order(4)
    void testGetTaskByDifferentUserReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/tasks/" + id)
                        .with(jwt().jwt(j -> j.subject("user2")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    void testUpdateTaskByDifferentUserReturnsNotFound() throws Exception {
        TaskCreateDto taskCreateDto = new TaskCreateDto("Hacked Title", "Hacked", false);

        mockMvc.perform(put("/api/tasks/" + id)
                        .with(jwt().jwt(j -> j.subject("user2")).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(taskCreateDto)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(6)
    void testDeleteTaskByDifferentUserReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/tasks/" + id)
                        .with(jwt().jwt(j -> j.subject("user2")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(7)
    void testDeleteOwnTask() throws Exception {
        mockMvc.perform(delete("/api/tasks/" + id)
                        .with(jwt().jwt(j -> j.subject("user1")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(8)
    void testDeleteTaskByAdmin() throws Exception {
        TaskCreateDto dto = new TaskCreateDto("Admin Delete Target", "Desc", false);
        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .with(jwt().jwt(j -> j.subject("user1")).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        TaskDto created = jsonMapper.readValue(result.getResponse().getContentAsString(), TaskDto.class);

        mockMvc.perform(delete("/api/tasks/" + created.getId())
                        .with(jwt().jwt(j -> j.subject("admin")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(9)
    void testGetTaskNotFound() throws Exception {
        mockMvc.perform(get("/api/tasks/99999")
                        .with(jwt().jwt(j -> j.subject("user1")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(10)
    void testFindTasksPaginated() throws Exception {
        taskRepository.deleteAll();

        for (int i = 1; i <= 3; i++) {
            TaskCreateDto dto = new TaskCreateDto("Task " + i, "Desc " + i, false);
            mockMvc.perform(post("/api/tasks")
                    .with(jwt().jwt(j -> j.subject("user1")).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(dto)));
        }

        mockMvc.perform(get("/api/tasks")
                        .param("page", "0")
                        .param("size", "2")
                        .with(jwt().jwt(j -> j.subject("user1")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @Order(11)
    void testFindTasksOnlyReturnsOwnedTasks() throws Exception {
        mockMvc.perform(get("/api/tasks")
                        .with(jwt().jwt(j -> j.subject("user2")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
