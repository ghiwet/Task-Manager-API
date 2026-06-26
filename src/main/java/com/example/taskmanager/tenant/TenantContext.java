package com.example.taskmanager.tenant;

/**
 * Holds the tenant for the current request thread. Populated by {@link TenantFilter}
 * from the JWT and consumed by {@link CurrentTenantResolver} to drive the per-connection
 * PostgreSQL session variable used by row-level security.
 */
public final class TenantContext {

    /** Tenant used when no tenant can be resolved (e.g. unauthenticated requests, startup). */
    public static final String DEFAULT_TENANT = "default";

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getTenantId() {
        String tenantId = CURRENT_TENANT.get();
        return tenantId != null ? tenantId : DEFAULT_TENANT;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
