package com.example.taskmanager;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * Test-only: supply a static keycloak client registration so the context does not perform OIDC
 * issuer discovery (a network call to Keycloak) at startup. Tests authenticate with mocked JWTs,
 * not the browser login flow, so dummy endpoints are fine — this keeps the tests hermetic (they run
 * with no Keycloak available, e.g. in CI). Ordered before the real client auto-configuration, whose
 * ClientRegistrationRepository is @ConditionalOnMissingBean and therefore backs off.
 */
@AutoConfiguration(before = OAuth2ClientAutoConfiguration.class)
public class TestOAuth2ClientConfig {

    @Bean
    @ConditionalOnMissingBean(ClientRegistrationRepository.class)
    ClientRegistrationRepository testClientRegistrationRepository() {
        ClientRegistration keycloak = ClientRegistration.withRegistrationId("keycloak")
                .clientId("test-client")
                .clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/keycloak")
                .scope("openid", "profile", "email")
                .authorizationUri("http://localhost/oauth2/authorize")
                .tokenUri("http://localhost/oauth2/token")
                .userInfoUri("http://localhost/oauth2/userinfo")
                .userNameAttributeName("preferred_username")
                .jwkSetUri("http://localhost/oauth2/jwks")
                .build();
        return new InMemoryClientRegistrationRepository(keycloak);
    }
}
