package com.example.taskmanager.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.Arrays;
import java.util.Map;

public class WithMockOAuth2UserSecurityContextFactory implements WithSecurityContextFactory<WithMockUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockUser annotation) {
        var context = SecurityContextHolder.createEmptyContext();
        var user = new DefaultOAuth2User(
                Arrays.stream(annotation.roles())
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList(),
                Map.of("sub", annotation.username()),
                "sub"
        );
        context.setAuthentication(new OAuth2AuthenticationToken(user, user.getAuthorities(), "google"));
        return context;
    }
}
