package com.example.taskmanager.security;

import com.example.taskmanager.AbstractIntegrationTest;
import com.example.taskmanager.TestcontainersConfig;
import com.example.taskmanager.dto.TaskCreateDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CSRF scoping (java/spring-disabled-csrf-protection): session-authenticated routes like /logout need a
 * CSRF token; the Bearer-only /api/** chain is exempt, which is safe because session cookies can't
 * authenticate it at all (threat #55).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@EmbeddedKafka(partitions = 1)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "rate-limit.enabled=false"
})
class LoginControllerCsrfTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void logoutWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/logout").with(oauth2Login()))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutWithCsrfTokenSucceeds() throws Exception {
        mockMvc.perform(post("/logout").with(oauth2Login()).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void renderedDashboardLogoutFormTokenIsAccepted() throws Exception {
        var dashboard = mockMvc.perform(get("/dashboard").with(oauth2Login()))
                .andExpect(status().isOk())
                .andReturn();
        String html = dashboard.getResponse().getContentAsString();
        var session = dashboard.getRequest().getSession(false);

        Matcher name = Pattern.compile("name=\"(_csrf|[^\"]*csrf[^\"]*)\"").matcher(html);
        Matcher value = Pattern.compile("name=\"[^\"]*csrf[^\"]*\"\\s+value=\"([^\"]+)\"").matcher(html);
        assertTrue(name.find(), "expected a CSRF hidden field name in the rendered dashboard HTML: " + html);
        assertTrue(value.find(), "expected a CSRF hidden field value in the rendered dashboard HTML: " + html);

        mockMvc.perform(post("/logout")
                        .session((MockHttpSession) session)
                        .with(oauth2Login())
                        .param(name.group(1), value.group(1)))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void apiRequestWithoutCsrfTokenStillSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .with(jwt().jwt(j -> j.subject("csrf-test-user").claim("preferred_username", "csrf-test-user").claim("tenant_id", "tenant-test"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")))
                        .contentType("application/json")
                        .content(jsonMapper.writeValueAsString(new TaskCreateDto("csrf exempt check", "desc", false))))
                .andExpect(status().isCreated());
    }

    /**
     * Threat #55: a logged-in browser session must not authenticate the API. Uses a real session, since
     * {@code .with(oauth2Login())} injects the authentication and would bypass the session lookup under test.
     */
    @Test
    void browserSessionCookieDoesNotAuthenticateTheApi() throws Exception {
        MockHttpSession session = loggedInBrowserSession();

        // Valid for the browser chain...
        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk());

        // ...but not for the API, CSRF token or not.
        String body = jsonMapper.writeValueAsString(new TaskCreateDto("csrf ride", "desc", false));
        mockMvc.perform(post("/api/v1/tasks").session(session).contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/tasks").session(session).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/tasks").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiDoesNotCreateSessions() throws Exception {
        var result = mockMvc.perform(get("/api/v1/tasks")
                        .with(jwt().jwt(j -> j.subject("stateless-user").claim("preferred_username", "stateless-user").claim("tenant_id", "tenant-test"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(result.getRequest().getSession(false) == null, "the API chain must not create an HTTP session");
    }

    private static MockHttpSession loggedInBrowserSession() {
        var user = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "browser-user", "name", "Browser User"), "sub");
        var authentication = new OAuth2AuthenticationToken(user, user.getAuthorities(), "keycloak");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(authentication));
        return session;
    }
}
