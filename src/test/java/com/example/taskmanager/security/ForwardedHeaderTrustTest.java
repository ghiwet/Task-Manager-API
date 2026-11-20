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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Threat #16: by default Tomcat trusts no proxy, so even a loopback/private peer (which Tomcat's own
 * default would trust) can't spoof X-Forwarded-For to get a fresh anonymous bucket. Needs a real server:
 * the RemoteIpValve doesn't run under MockMvc.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@EmbeddedKafka(partitions = 1)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "rate-limit.public-requests-per-minute=3"
})
class ForwardedHeaderTrustTest extends AbstractIntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private Environment environment;

    @Test
    void loopbackPeerCannotSpoofItsIpByDefault() throws Exception {
        int apiPort = environment.getRequiredProperty("local.server.port", Integer.class);

        for (int i = 0; i < 3; i++) {
            assertThat(getLogin(apiPort).statusCode()).isNotEqualTo(429);
        }
        assertThat(getLogin(apiPort).statusCode()).as("rotated X-Forwarded-For is ignored").isEqualTo(429);
    }

    private HttpResponse<String> getLogin(int port) throws Exception {
        String spoofed = "198.51.100." + ThreadLocalRandom.current().nextInt(1, 255);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/login"))
                .header("X-Forwarded-For", spoofed)
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
