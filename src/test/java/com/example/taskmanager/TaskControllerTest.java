package com.example.taskmanager;

import com.example.taskmanager.dto.TaskCreateDto;
import com.example.taskmanager.dto.TaskDto;
import com.example.taskmanager.model.Task;
import com.example.taskmanager.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TaskControllerTest {


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskCreateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Test Title"));
    }

    @Test
    @Order(2)
    void testGetTask() throws Exception {
        Task task = taskRepository.save(new Task(null, 0, "Fetch Title", "Fetch Description", false, null, null));


        MvcResult result = mockMvc.perform(get("/api/tasks/" + task.getId()))
                .andExpect(status().isOk())
                .andReturn();

        TaskDto responseTask = objectMapper.readValue(result.getResponse().getContentAsString(), TaskDto.class);

        assertNotNull(responseTask.getId());
        assertEquals("Fetch Title", responseTask.getTitle());
        id = responseTask.getId();
    }

    @Test
    @Order(3)
    void tesUpdateTask() throws Exception {
        TaskCreateDto taskCreateDto = new TaskCreateDto("Modified Title", "Modified Description", true);

        MvcResult result = mockMvc
                .perform(put("/api/tasks/" + id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(taskCreateDto)))
                .andExpect(status().isOk())
                .andReturn();

        TaskDto responseTask = objectMapper.readValue(result.getResponse().getContentAsString(), TaskDto.class);

        assertEquals(responseTask.getId(), id);
        assertEquals("Modified Title", responseTask.getTitle());
    }

    @Test
    @Order(4)
    void testDeleteTask() throws Exception {
        Task task = taskRepository.save(new Task(null, 0, "Fetch Title", "Fetch Description", false, null, null));

        mockMvc.perform(delete("/api/tasks/" + task.getId()))
                .andExpect(status().isOk());
    }
}

