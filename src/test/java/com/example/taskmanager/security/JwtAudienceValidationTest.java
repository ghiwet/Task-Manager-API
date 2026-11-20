package com.example.taskmanager.security;

import com.example.taskmanager.AbstractIntegrationTest;
import com.example.taskmanager.TestcontainersConfig;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Threat #9: the resource server must reject tokens that weren't issued for this API, even when
 * they're validly signed by the right issuer. Unlike the other tests (which use the jwt() post-processor
 * and so skip decoding entirely), these send real signed Bearer tokens through the app's actual
 * Boot-built JwtDecoder. It's pointed at a test RSA key via public-key-location so the test stays
 * hermetic. Boot only picks the public-key decoder when issuer-uri is blank (otherwise it does issuer
 * discovery against the real Keycloak), so issuer-uri is cleared here; the audience validator comes from
 * the same Boot validator chain the issuer-uri decoder uses, driven by the real
 * spring.security.oauth2.resourceserver.jwt.audiences property.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@EmbeddedKafka(partitions = 1)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "rate-limit.enabled=false"
})
class JwtAudienceValidationTest extends AbstractIntegrationTest {

    private static final String ISSUER = "http://localhost:8082/realms/myrealm";
    private static final KeyPair KEYS = generateKeys();
    private static final Path PUBLIC_KEY = writePublicKey(KEYS);

    @DynamicPropertySource
    static void decoderProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.public-key-location", () -> PUBLIC_KEY.toUri().toString());
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void tokenIssuedForThisApiIsAccepted() throws Exception {
        mockMvc.perform(get("/api/v1/tasks").header("Authorization", "Bearer " + token(List.of("task-manager-api"))))
                .andExpect(status().isOk());
    }

    @Test
    void tokenListingThisApiAmongOtherAudiencesIsAccepted() throws Exception {
        mockMvc.perform(get("/api/v1/tasks").header("Authorization", "Bearer " + token(List.of("account", "task-manager-api"))))
                .andExpect(status().isOk());
    }

    @Test
    void tokenWithoutAnAudienceIsRejected() throws Exception {
        // What myrealm issued before the audience mapper: validly signed, right issuer, no aud.
        mockMvc.perform(get("/api/v1/tasks").header("Authorization", "Bearer " + token(List.of())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenIssuedForAnotherClientIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/tasks").header("Authorization", "Bearer " + token(List.of("account", "some-other-app"))))
                .andExpect(status().isUnauthorized());
    }

    private static String token(List<String> audiences) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("aud-test-user")
                .claim("preferred_username", "aud-test-user")
                .claim("tenant_id", "tenant-test")
                .claim("realm_access", Map.of("roles", List.of("USER")))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)));
        if (!audiences.isEmpty()) {
            claims.audience(audiences);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner((RSAPrivateKey) KEYS.getPrivate()));
        return jwt.serialize();
    }

    private static KeyPair generateKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path writePublicKey(KeyPair keys) {
        try {
            String pem = "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keys.getPublic().getEncoded())
                    + "\n-----END PUBLIC KEY-----\n";
            Path file = Files.createTempFile("jwt-audience-test", ".pem");
            Files.writeString(file, pem);
            file.toFile().deleteOnExit();
            return file;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
