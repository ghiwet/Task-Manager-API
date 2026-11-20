package com.example.taskmanager.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Binds the JWT's {@code tenant_id} claim to {@link TenantContext} for the request and always clears it
 * afterwards, so no tenant leaks across pooled threads. Runs in the {@code /api/**} chain only, after the
 * bearer token filter.
 *
 * <p>Fails closed (threat #52): an authenticated request with no usable tenant gets 403 instead of running
 * under {@link TenantContext#UNRESOLVED_TENANT}, which would otherwise be one shared, writable tenant for
 * every tenant-less caller. Unauthenticated requests pass through so Spring Security answers them with 401.
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

    static final String TENANT_CLAIM = "tenant_id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String tenantId = resolveTenant(auth);
            if (tenantId != null) {
                TenantContext.setTenantId(tenantId);
            } else if (isAuthenticated(auth)) {
                rejectMissingTenant(response);
                return;
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private static String resolveTenant(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String tenantId = jwtAuth.getToken().getClaimAsString(TENANT_CLAIM);
            // A claim equal to the sentinel counts as missing.
            if (tenantId != null && !tenantId.isBlank() && !TenantContext.UNRESOLVED_TENANT.equals(tenantId)) {
                return tenantId;
            }
        }
        return null;
    }

    private static boolean isAuthenticated(Authentication auth) {
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    private static void rejectMissingTenant(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(
                "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,"
                        + "\"detail\":\"No tenant is associated with this identity.\"}"
        );
    }
}
