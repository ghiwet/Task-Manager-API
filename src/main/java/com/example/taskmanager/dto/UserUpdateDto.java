package com.example.taskmanager.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-service user update. Only the password is bindable; {@code roles} is intentionally
 * absent so a caller cannot escalate their own privileges by putting it in the request body.
 * A null password means "leave it unchanged"; when present it must meet the same strength
 * policy as registration.
 */
public record UserUpdateDto(
        @Size(min = 8, max = 128)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).+$",
                message = "Password must contain an uppercase letter, a lowercase letter, a digit and a special character"
        )
        String password) {
}
