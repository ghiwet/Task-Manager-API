package com.example.taskmanager.tenant;

import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the tenant resolver and connection provider into Hibernate so every session is bound
 * to the resolved tenant via the {@code app.current_tenant} PostgreSQL session variable. Spring
 * Boot does not auto-detect these beans, so they are registered explicitly here.
 */
@Configuration
public class MultiTenancyConfig {

    @Bean
    HibernatePropertiesCustomizer multiTenancyCustomizer(TenantConnectionProvider connectionProvider,
                                                         CurrentTenantResolver tenantResolver) {
        return properties -> {
            properties.put(MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
        };
    }
}
