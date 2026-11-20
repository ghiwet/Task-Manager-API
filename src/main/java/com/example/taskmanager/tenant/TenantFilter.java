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
 * Resolves the tenant from the authenticated JWT's {@code tenant_id} claim and binds it to
 * {@link TenantContext} for the duration of the request. Runs after the bearer token filter so
 * the authentication is already established. Always clears the context afterwards to prevent
 * tenant leakage across pooled request threads.
 *
 * <p>Fails closed on the API (threat #52): an authenticated {@code /api/**} request with no
 * resolvable tenant -- a JWT missing the claim, or an oauth2Login browser session, which carries
 * no JWT -- is rejected with 403 instead of running under {@link TenantContext#UNRESOLVED_TENANT}.
 * That sentinel only default-denies reads; if writes were allowed under it, every tenant-less
 * caller would share one real, writable tenant. Unauthenticated requests pass through so Spring
 * Security still answers them with 401.
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
            } else if (isApiRequest(request) && isAuthenticated(auth)) {
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
            // A claim literally equal to the sentinel is treated as missing, never as a tenant.
            if (tenantId != null && !tenantId.isBlank() && !TenantContext.UNRESOLVED_TENANT.equals(tenantId)) {
                return tenantId;
            }
        }
        return null;
    }

    private static boolean isApiRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/api/");
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
