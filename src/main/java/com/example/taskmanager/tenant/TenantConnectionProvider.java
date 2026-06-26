package com.example.taskmanager.tenant;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Sets the {@code app.current_tenant} PostgreSQL session variable on each connection handed to
 * Hibernate, and clears it before the connection returns to the pool. Combined with the RLS
 * policies, this guarantees every statement is scoped to the resolved tenant at the database
 * layer — even a query that forgets to filter by tenant cannot read another tenant's rows.
 */
@Component
public class TenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private static final String SET_TENANT = "SELECT set_config('app.current_tenant', ?, false)";
    private static final String CLEAR_TENANT = "SELECT set_config('app.current_tenant', '', false)";

    private final DataSource dataSource;

    public TenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        // Tenant-agnostic connection: clear any tenant left over from a previous pool user
        // so tenant-independent work never runs under a stale tenant.
        Connection connection = dataSource.getConnection();
        clearTenant(connection);
        return connection;
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        try {
            clearTenant(connection);
        } finally {
            connection.close();
        }
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        try (PreparedStatement ps = connection.prepareStatement(SET_TENANT)) {
            ps.setString(1, tenantIdentifier);
            ps.execute();
        }
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try {
            clearTenant(connection);
        } finally {
            connection.close();
        }
    }

    private void clearTenant(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(CLEAR_TENANT)) {
            ps.execute();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return null;
    }
}
