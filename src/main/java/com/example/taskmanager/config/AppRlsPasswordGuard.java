package com.example.taskmanager.config;

import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Checks the {@code app_rls_password} Flyway placeholder before any migration runs.
 * {@code R__app_rls_password.sql} splices it into a SQL string literal and runs as the owner role, so a
 * single quote (or backslash, or a control character) could break the migration or inject SQL with
 * owner privileges. Only printable ASCII without those characters is accepted. An empty value is left
 * to the migration, which fails with a clearer message.
 */
@Component
public class AppRlsPasswordGuard implements FlywayConfigurationCustomizer {

    static final String PLACEHOLDER = "app_rls_password";
    private static final Pattern SAFE = Pattern.compile("[\\x20-\\x7E&&[^'\\\\]]*");

    @Override
    public void customize(org.flywaydb.core.api.configuration.FluentConfiguration configuration) {
        check(configuration.getPlaceholders().get(PLACEHOLDER));
    }

    static void check(String password) {
        if (password != null && !SAFE.matcher(password).matches()) {
            throw new IllegalStateException("spring.datasource.password (the app_rls password) may only contain "
                    + "printable ASCII without ' or \\, because a Flyway migration applies it as a SQL literal");
        }
    }
}
