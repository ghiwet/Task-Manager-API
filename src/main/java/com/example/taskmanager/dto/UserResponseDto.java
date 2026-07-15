package com.example.taskmanager.dto;

import com.example.taskmanager.enumration.Role;
import com.example.taskmanager.model.AppUser;

import java.util.Set;

/**
 * Public view of a user. Deliberately omits the password hash and the internal
 * {@code tenantId} so neither is ever serialized to a client.
 */
public record UserResponseDto(Long id, String username, Set<Role> roles) {

    public static UserResponseDto from(AppUser user) {
        return new UserResponseDto(user.getId(), user.getUsername(), user.getRoles());
    }
}
