package com.example.taskmanager.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Tells Hibernate which tenant the current unit of work belongs to. Hibernate passes the
 * returned identifier to {@link TenantConnectionProvider}, which sets it as the PostgreSQL
 * session variable enforced by row-level security.
 */
@Component
public class CurrentTenantResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.getTenantId();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
