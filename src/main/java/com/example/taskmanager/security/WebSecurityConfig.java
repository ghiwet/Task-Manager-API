package com.example.taskmanager.security;

import com.example.taskmanager.tenant.TenantFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
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
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Three filter chains, most specific first:
 * <ol>
 *   <li>{@code actuator} — the management port and {@code /actuator/**}: stateless, no login, no rate limit.</li>
 *   <li>{@code api} — {@code /api/**}: stateless, Bearer-JWT only, so session cookies can't call it (threat #55).</li>
 *   <li>{@code browser} — everything else: oauth2Login sessions with CSRF on.</li>
 * </ol>
 * Not loaded in the {@code migrate} profile (Helm migration Job, threat #18): oauth2Login's client
 * registration does OIDC discovery at startup, which would block that Job on Keycloak.
 */
@Profile("!migrate")
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableScheduling
public class WebSecurityConfig {

    private static final String[] PUBLIC_ACTUATOR_PATHS = {
            "/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus"};

    private final RateLimitFilter rateLimitFilter;
    private final TenantFilter tenantFilter;

    public WebSecurityConfig(RateLimitFilter rateLimitFilter, TenantFilter tenantFilter) {
        this.rateLimitFilter = rateLimitFilter;
        this.tenantFilter = tenantFilter;
    }

    /**
     * Actuator: probes and the Prometheus scrape. Only health/info/prometheus are reachable; anything else
     * gets a plain 403 (no login redirect, no session). Exempt from rate limiting, so kubelet probes sharing
     * a node IP can't throttle themselves into restart loops. Matches every request on the management port,
     * not just {@code /actuator/**}, so nothing there falls through to the browser login chain.
     */
    @Bean
    @Order(0)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http, Environment environment) throws Exception {
        http
                .securityMatcher(managementRequests(environment))
                .csrf(csrf -> csrf.disable()) // stateless, read-only endpoints, no cookie auth
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(this::securityHeaders)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ACTUATOR_PATHS).permitAll()
                        .anyRequest().denyAll()
                );

        return http.build();
    }

    /**
     * The REST API: stateless and Bearer-token-only. With no oauth2Login and no session, a browser's
     * JSESSIONID authenticates nothing here (401), so CSRF protection has nothing to protect (threat #55).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable()) // safe: stateless, Authorization header only
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
     * Everything else: the cookie-session browser flow (oauth2Login, /dashboard, /logout) and Swagger UI,
     * with CSRF protection on.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain browserSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .headers(this::securityHeaders)
                .addFilterAfter(rateLimitFilter, AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
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

    /**
     * Requests on the management port (resolved lazily: it's only known once that server starts), plus
     * {@code /actuator/**} on any port so the API port answers it without the login chain.
     */
    private static RequestMatcher managementRequests(Environment environment) {
        return request -> isManagementPort(request, environment) || isActuatorPath(request);
    }

    private static boolean isManagementPort(HttpServletRequest request, Environment environment) {
        Integer managementPort = environment.getProperty("local.management.port", Integer.class);
        return managementPort != null && managementPort == request.getLocalPort();
    }

    private static boolean isActuatorPath(HttpServletRequest request) {
        String path = RequestPaths.withinApplication(request);
        return path.equals("/actuator") || path.startsWith("/actuator/");
    }

    // Disable Boot's global servlet registration of these @Component filters; they run only in the chains.
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<TenantFilter> tenantFilterRegistration(TenantFilter filter) {
        FilterRegistrationBean<TenantFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
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

    /**
     * Maps a Keycloak JWT to an authentication: principal = {@code preferred_username} (also the task
     * owner, rather than the opaque {@code sub}), authorities = {@code realm_access.roles} with a
     * {@code ROLE_} prefix. A token without realm roles yields no authorities.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
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

    /** Lets the React SPA (another origin) call the API; Bearer tokens need no credentials/cookies. */
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
