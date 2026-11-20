package com.example.taskmanager.security;

import com.example.taskmanager.tenant.TenantFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// The migrate profile (Helm migration Job, threat #18) runs with no web server and no Keycloak
// reachability guarantee; oauth2Login's ClientRegistrationRepository does eager OIDC issuer
// discovery at bean-creation time, which would otherwise block that Job's startup on Keycloak.
@Profile("!migrate")
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableScheduling
public class WebSecurityConfig {

    private final RateLimitFilter rateLimitFilter;
    private final TenantFilter tenantFilter;

    public WebSecurityConfig(RateLimitFilter rateLimitFilter, TenantFilter tenantFilter) {
        this.rateLimitFilter = rateLimitFilter;
        this.tenantFilter = tenantFilter;
    }

    /**
     * The REST API: Bearer-token-only and stateless (threat #55). It has no oauth2Login and never reads
     * the HTTP session, so a browser's JSESSIONID cookie -- the ambient credential CSRF abuses --
     * authenticates nothing here; a request without a valid Bearer token gets 401. With no cookie-based
     * authentication on this chain, CSRF protection has nothing to protect, so it is disabled here only.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                // CodeQL java/spring-disabled-csrf-protection: safe because this chain is STATELESS and
                // authenticates only via the Authorization header (no session, no cookies).
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .headers(this::securityHeaders)
                .addFilterAfter(tenantFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/tasks/**").hasAuthority("ROLE_USER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/tasks/**").hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")
                        .requestMatchers("/api/v1/assistant/**").hasAuthority("ROLE_USER")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );

        return http.build();
    }

    /**
     * Everything else: the cookie-session browser flow (oauth2Login, /dashboard, /logout), Swagger UI and
     * actuator. CSRF protection is fully on here; there is no Bearer authentication on this chain.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain browserSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .headers(this::securityHeaders)
                .addFilterAfter(rateLimitFilter, BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Only the exposed actuator endpoints are public, and they're served on the
                        // internal management port (management.server.port), not the API port. The
                        // Security filter chain applies to that port too, hence these rules (threat #17).
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth
                        .loginPage("/login")
                        .defaultSuccessUrl("/dashboard", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                );

        return http.build();
    }

    private void securityHeaders(HeadersConfigurer<HttpSecurity> headers) {
        headers
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .preload(true)
                        .maxAgeInSeconds(31536000))
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(frame -> frame.deny())
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; script-src 'self' 'unsafe-inline'; " +
                        "style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; form-action 'self'"))
                .referrerPolicy(referrer -> referrer
                        .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicyHeader(permissions -> permissions
                        .policy("camera=(), microphone=(), geolocation=()"));
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        // Principal is the Keycloak username (so the task owner is too), not the opaque sub UUID.
        converter.setPrincipalClaimName("preferred_username");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // A valid token may carry no realm roles, so realm_access (or its roles entry) can be
            // absent; treat that as no authorities instead of dereferencing null.
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
                return List.of();
            }
            return roles.stream()
                    .map(String::valueOf)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList());
        });
        return converter;
    }

    // Allow the React SPA (a different origin) to call the JWT-protected API. Bearer tokens travel in
    // the Authorization header, so no credentials/cookies are needed.
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("X-Rate-Limit-Remaining", "Retry-After"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}