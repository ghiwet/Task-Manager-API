package com.example.taskmanager.tenant;

/**
 * Holds the tenant for the current request thread. Populated by {@link TenantFilter}
 * from the JWT and consumed by {@link CurrentTenantResolver} to drive the per-connection
 * PostgreSQL session variable used by row-level security.
 */
public final class TenantContext {

    /**
     * Bound when no tenant is resolved. A reserved sentinel no real tenant uses, so RLS matches no
     * rows and default-denies instead of falling back to a populated bucket (e.g. {@code 'default'}).
     */
    public static final String UNRESOLVED_TENANT = "__unresolved__";

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getTenantId() {
        String tenantId = CURRENT_TENANT.get();
        return tenantId != null ? tenantId : UNRESOLVED_TENANT;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
