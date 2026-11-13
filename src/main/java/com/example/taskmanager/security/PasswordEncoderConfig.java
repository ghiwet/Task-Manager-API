package com.example.taskmanager.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Split out of WebSecurityConfig: AppUserService needs this for password hashing regardless of
 * profile, but WebSecurityConfig itself is excluded under "migrate" (the Helm migration Job has
 * no web server and no Keycloak reachability guarantee).
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
