package com.example.taskmanager.tenant;

import com.example.taskmanager.TestcontainersConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The app_rls password is set from config by {@code R__app_rls_password}, not left at V5's hard-coded
 * dev default. Runs Flyway directly against a fresh Postgres to show the configured password is
 * applied, that changing it rotates the role's password, and that an empty value fails the migration.
 */
class AppRlsPasswordMigrationTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(TestcontainersConfig.POSTGRES_IMAGE);

    @BeforeAll
    static void start() {
        postgres.start();
    }

    @AfterAll
    static void stop() {
        postgres.stop();
    }

    private static void migrate(String appRlsPassword) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migrations")
                .placeholders(Map.of("app_rls_password", appRlsPassword))
                .load()
                .migrate();
    }

    private static boolean canLogIn(String password) {
        try (var ignored = DriverManager.getConnection(postgres.getJdbcUrl(), "app_rls", password)) {
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    @Test
    void configuredPasswordIsAppliedAndRotated() {
        migrate("first-secret");
        assertThat(canLogIn("first-secret")).isTrue();
        assertThat(canLogIn("app_rls_pass")).as("V5's dev default no longer works").isFalse();

        migrate("second-secret");
        assertThat(canLogIn("second-secret")).as("a changed value re-runs the repeatable migration").isTrue();
        assertThat(canLogIn("first-secret")).isFalse();

        assertThatThrownBy(() -> migrate(""))
                .as("an unset password fails the migration instead of blanking the role's password")
                .isInstanceOf(FlywayException.class);
        assertThat(canLogIn("second-secret")).isTrue();
    }
}
