package com.example.taskmanager.dto;

import com.example.taskmanager.enumration.Role;
import com.example.taskmanager.model.AppUser;

import java.util.Set;

/** Public view of a user: no password hash, no tenantId. */
public record UserResponseDto(Long id, String username, Set<Role> roles) {

    public static UserResponseDto from(AppUser user) {
        return new UserResponseDto(user.getId(), user.getUsername(), user.getRoles());
    }
}
