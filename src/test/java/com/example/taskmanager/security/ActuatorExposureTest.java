package com.example.taskmanager.security;

import com.example.taskmanager.AbstractIntegrationTest;
import com.example.taskmanager.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Threat #17: actuator must not be reachable on the API port (the only port the Service/ingress
 * exposes), and on the internal management port only health/info/prometheus are exposed, with
 * health details hidden from anonymous callers. Runs a real embedded server because MockMvc never
 * sees the separate management port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@EmbeddedKafka(partitions = 1)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "rate-limit.enabled=false"
})
class ActuatorExposureTest extends AbstractIntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private Environment environment;

    @Test
    void actuatorIsNotServedOnTheApiPort() throws Exception {
        int apiPort = environment.getRequiredProperty("local.server.port", Integer.class);
        int managementPort = environment.getRequiredProperty("local.management.port", Integer.class);
        assertThat(managementPort).as("actuator runs on its own port").isNotEqualTo(apiPort);

        // Publicly-permitted actuator paths pass security and then find no handler on the API port.
        for (String path : new String[]{"/actuator/prometheus", "/actuator/health"}) {
            assertThat(get(apiPort, path).statusCode()).as("API port must not serve %s", path).isEqualTo(404);
        }
        // Everything else under /actuator is no longer permitAll, so anonymous callers are stopped
        // even before routing (401); either way it's never served.
        for (String path : new String[]{"/actuator/metrics", "/actuator/env", "/actuator/heapdump"}) {
            assertThat(get(apiPort, path).statusCode()).as("API port must not serve %s", path).isNotEqualTo(200);
        }
    }

    @Test
    void managementPortServesOnlyTheExposedEndpoints() throws Exception {
        int managementPort = environment.getRequiredProperty("local.management.port", Integer.class);

        HttpResponse<String> prometheus = get(managementPort, "/actuator/prometheus");
        assertThat(prometheus.statusCode()).isEqualTo(200);
        assertThat(prometheus.body()).contains("jvm_memory_used_bytes");

        // Health may be UP or DOWN depending on which backing services the test brings up; either
        // way anonymous callers get the status only, never component details.
        HttpResponse<String> health = get(managementPort, "/actuator/health");
        assertThat(health.statusCode()).isIn(200, 503);
        assertThat(health.body()).contains("\"status\"").doesNotContain("\"components\"");

        for (String path : new String[]{"/actuator/metrics", "/actuator/env", "/actuator/heapdump", "/actuator/loggers"}) {
            assertThat(get(managementPort, path).statusCode()).as("%s must not be exposed", path).isNotEqualTo(200);
        }
    }

    private HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
