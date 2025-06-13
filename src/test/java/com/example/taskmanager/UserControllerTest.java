package com.example.taskmanager;

import com.example.taskmanager.model.AppUser;
import com.example.taskmanager.repository.AppUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;


import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;


    private String username;

    @BeforeAll
    void setup() {
        userRepository.deleteAll();
    }

    @Test
    @Order(1)
    void testCreateUser() throws Exception {
        Map<String, Object> user = Map.of(
                "username", "user1",
                "password", "pass1"
        );


        MvcResult result = mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isCreated())
                .andReturn();

        AppUser userResponse = objectMapper.readValue(result.getResponse().getContentAsString(), AppUser.class);

        assertNotNull(userResponse.getId());
        assertEquals("user1", userResponse.getUsername());
        username = userResponse.getUsername();

        //create another user
        Map<String, Object> user2 = Map.of(
                "username", "user2",
                "password", "pass2"
        );


        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user2)))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(2)
    @WithMockUser(username = "user1")
    void testGetUser() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andReturn();

        AppUser userResponse = objectMapper.readValue(result.getResponse().getContentAsString(), AppUser.class);

        assertEquals("user1", userResponse.getUsername());
        assertTrue(passwordEncoder.matches("pass1", userResponse.getPassword()));
    }

    @Test
    @Order(3)
    @WithMockUser(username = "user3")
    void testGetUserNotFound() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(4)
    @WithMockUser(username = "user1")
    void testUpdateUser() throws Exception {
        Map<String, Object> user = Map.of(
                "password", "newPass"
        );
        MvcResult result = mockMvc
                .perform(put("/api/users/" + username)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isOk())
                .andReturn();

        AppUser userResponse = objectMapper.readValue(result.getResponse().getContentAsString(), AppUser.class);
        assertTrue(passwordEncoder.matches("newPass", userResponse.getPassword()));
    }

    @Test
    @Order(5)
    @WithMockUser(username = "user2")
    void testUpdateUserForbidden() throws Exception {
        Map<String, Object> user = Map.of(
                "password", "anotherPass"
        );
        mockMvc
                .perform(put("/api/users/" + username)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateUserByAdmin() throws Exception {
        Map<String, Object> user = Map.of(
                "password", "anotherPass"
        );
        mockMvc
                .perform(put("/api/users/" + username)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isOk());
    }


    @Test
    @Order(7)
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteUserByAdmin() throws Exception {
        mockMvc
                .perform(delete("/api/users/" + username)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }
}
