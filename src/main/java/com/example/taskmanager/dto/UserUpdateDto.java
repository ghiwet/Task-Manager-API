package com.example.taskmanager.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-service user update. Only the password is bindable — no {@code roles} field, so a caller
 * can't escalate their own privileges. A null password leaves it unchanged.
 */
public record UserUpdateDto(
        @Size(min = 8, max = 128)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).+$",
                message = "Password must contain an uppercase letter, a lowercase letter, a digit and a special character"
        )
        String password) {
}
