package com.example.taskmanager;

import com.example.taskmanager.model.AppUser;
import com.example.taskmanager.repository.AppUserRepository;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

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
                "password", "Pass1!word"
        );

        MvcResult result = mockMvc.perform(post("/api/users/register")
                        .with(jwt().jwt(j -> j.subject("user1")).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(user)))
                .andExpect(status().isCreated())
                .andReturn();

        AppUser userResponse = jsonMapper.readValue(result.getResponse().getContentAsString(), AppUser.class);

        assertNotNull(userResponse.getId());
        assertEquals("user1", userResponse.getUsername());
        username = userResponse.getUsername();

        Map<String, Object> user2 = Map.of(
                "username", "user2",
                "password", "Pass2!word"
        );

        mockMvc.perform(post("/api/users/register")
                        .with(jwt().jwt(j -> j.subject("user2")).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(user2)))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(2)
    void testGetUser() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .with(jwt().jwt(j -> j.subject("user1")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andReturn();

        AppUser userResponse = jsonMapper.readValue(result.getResponse().getContentAsString(), AppUser.class);

        assertEquals("user1", userResponse.getUsername());
        assertTrue(passwordEncoder.matches("Pass1!word", userResponse.getPassword()));
    }

    @Test
    @Order(3)
    void testGetUserNotFound() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .with(jwt().jwt(j -> j.subject("user3")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(4)
    void testUpdateUser() throws Exception {
        Map<String, Object> user = Map.of(
                "password", "newPass"
        );
        MvcResult result = mockMvc
                .perform(put("/api/users/" + username)
                        .with(jwt().jwt(j -> j.subject("user1")).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(user)))
                .andExpect(status().isOk())
                .andReturn();

        AppUser userResponse = jsonMapper.readValue(result.getResponse().getContentAsString(), AppUser.class);
        assertTrue(passwordEncoder.matches("newPass", userResponse.getPassword()));
    }

    @Test
    @Order(5)
    void testUpdateUserForbidden() throws Exception {
        Map<String, Object> user = Map.of(
                "password", "anotherPass"
        );
        mockMvc
                .perform(put("/api/users/" + username)
                        .with(jwt().jwt(j -> j.subject("user2")).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    void testUpdateUserByAdmin() throws Exception {
        Map<String, Object> user = Map.of(
                "password", "anotherPass"
        );
        mockMvc
                .perform(put("/api/users/" + username)
                        .with(jwt().jwt(j -> j.subject("admin")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(user)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(7)
    void testDeleteUserByAdmin() throws Exception {
        mockMvc
                .perform(delete("/api/users/" + username)
                        .with(jwt().jwt(j -> j.subject("admin")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(8)
    void testRegisterWithWeakPasswordReturnsBadRequest() throws Exception {
        Map<String, Object> user = Map.of(
                "username", "weakuser",
                "password", "password"
        );

        mockMvc.perform(post("/api/users/register")
                        .with(jwt().jwt(j -> j.subject("weakuser")).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(user)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(9)
    void testRegisterWithShortUsernameReturnsBadRequest() throws Exception {
        Map<String, Object> user = Map.of(
                "username", "ab",
                "password", "Pass1!word"
        );

        mockMvc.perform(post("/api/users/register")
                        .with(jwt().jwt(j -> j.subject("ab")).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(user)))
                .andExpect(status().isBadRequest());
    }
}
