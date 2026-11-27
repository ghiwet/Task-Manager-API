package com.example.taskmanager.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The app_rls password is spliced into a SQL literal run as the owner; unsafe values never reach Flyway.
class AppRlsPasswordGuardTest {

    private final AppRlsPasswordGuard guard = new AppRlsPasswordGuard();

    private void customizeWith(String password) {
        guard.customize(Flyway.configure().placeholders(Map.of(AppRlsPasswordGuard.PLACEHOLDER, password)));
    }

    @Test
    void acceptsTypicalPasswords() {
        assertThatCode(() -> customizeWith("app_rls_pass")).doesNotThrowAnyException();
        assertThatCode(() -> customizeWith("3f9a0c4e7b1d2a5f8e6c9b0a1d4e7f2a3b5c8d0e1f4a6b9c")).doesNotThrowAnyException();
        assertThatCode(() -> customizeWith("A-b_c.d!e@f#g$h%^&*()+=[]{}|;:,<>?/~`\"")).doesNotThrowAnyException();
        assertThatCode(() -> customizeWith("")).as("empty is left to the migration's own check").doesNotThrowAnyException();
    }

    @Test
    void rejectsValuesThatCouldBreakOutOfTheLiteral() {
        assertThatThrownBy(() -> customizeWith("x'; ALTER ROLE app_rls SUPERUSER; --"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> customizeWith("back\\slash")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> customizeWith("new\nline")).isInstanceOf(IllegalStateException.class);
    }
}
