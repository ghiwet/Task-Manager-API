package com.example.taskmanager.tenant;

import com.example.taskmanager.TestcontainersConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V13 trims descriptions written before V11's limit and validates the constraint, so no older row is
 * left that fails the check on its next UPDATE (threat #54 follow-up).
 */
class DescriptionLimitMigrationTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(TestcontainersConfig.POSTGRES_IMAGE);

    @BeforeAll
    static void start() {
        postgres.start();
    }

    @AfterAll
    static void stop() {
        postgres.stop();
    }

    private static void migrateTo(String target) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migrations")
                .placeholders(Map.of("app_rls_password", "app_rls_pass"))
                .target(target)
                .load()
                .migrate();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @Test
    void overLongLegacyRowsAreTrimmedAndTheConstraintValidated() throws SQLException {
        migrateTo("10");
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO tasks (title, description, completed, owner, tenant_id) "
                    + "VALUES ('legacy', repeat('a', 6000), false, 'u', 'tenant-a')");
        }

        migrateTo("latest");

        try (Connection c = connect(); Statement s = c.createStatement()) {
            ResultSet length = s.executeQuery("SELECT char_length(description) FROM tasks WHERE title = 'legacy'");
            length.next();
            assertThat(length.getInt(1)).isEqualTo(4000);

            ResultSet validated = s.executeQuery(
                    "SELECT convalidated FROM pg_constraint WHERE conname = 'chk_tasks_description_length'");
            validated.next();
            assertThat(validated.getBoolean(1)).as("constraint now covers every row").isTrue();

            s.execute("UPDATE tasks SET completed = true WHERE title = 'legacy'");
        }
    }
}
