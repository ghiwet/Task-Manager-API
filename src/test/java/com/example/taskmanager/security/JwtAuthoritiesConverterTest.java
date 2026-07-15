package com.example.taskmanager.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthoritiesConverterTest {

    // The filters are only used inside securityFilterChain, not by jwtAuthenticationConverter().
    private final JwtAuthenticationConverter converter =
            new WebSecurityConfig(null, null).jwtAuthenticationConverter();

    private Jwt.Builder baseJwt() {
        return Jwt.withTokenValue("token").header("alg", "none").subject("user1");
    }

    // Only the ROLE_ authorities are contributed by our converter; Spring Security 7 also adds a
    // framework FACTOR_BEARER authority that is irrelevant to our hasAuthority(...) rules.
    private Set<String> roleAuthoritiesOf(Jwt jwt) {
        return converter.convert(jwt).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .collect(Collectors.toSet());
    }

    @Test
    void mapsRealmRolesToPrefixedAuthorities() {
        Jwt jwt = baseJwt()
                .claim("realm_access", Map.of("roles", List.of("USER", "ADMIN")))
                .build();
        assertEquals(Set.of("ROLE_USER", "ROLE_ADMIN"), roleAuthoritiesOf(jwt));
    }

    @Test
    void missingRealmAccessClaimYieldsNoRoles() {
        assertTrue(roleAuthoritiesOf(baseJwt().build()).isEmpty());
    }

    @Test
    void realmAccessWithoutRolesYieldsNoRoles() {
        Jwt jwt = baseJwt().claim("realm_access", Map.of("account", List.of("view-profile"))).build();
        assertTrue(roleAuthoritiesOf(jwt).isEmpty());
    }
}
