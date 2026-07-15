package com.example.taskmanager.controller;

import com.example.taskmanager.dto.UserRegistrationDto;
import com.example.taskmanager.dto.UserResponseDto;
import com.example.taskmanager.dto.UserUpdateDto;
import com.example.taskmanager.exception.AppUserNotFoundException;
import com.example.taskmanager.model.AppUser;
import com.example.taskmanager.service.AppUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping(path = "/api/v1/users")
public class UserController {
    private final AppUserService userService;

    public UserController(AppUserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getCurrentUser(Authentication authentication) {
        String username = authentication.getName();
        AppUser user = userService.findUserByUserName(username);
        if(Objects.isNull(user)) {
            throw new AppUserNotFoundException("User not found");
        }
        return ResponseEntity.ok(UserResponseDto.from(user));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponseDto>> getAllUsers() {
        List<UserResponseDto> users = userService.getAllUsers().stream()
                .map(UserResponseDto::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponseDto> registerUser(@RequestBody @Valid UserRegistrationDto registration) {
        AppUser newUser = new AppUser();
        newUser.setUsername(registration.getUsername());
        newUser.setPassword(registration.getPassword());
        AppUser created = userService.registerNewUser(newUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponseDto.from(created));
    }

    @PutMapping("/{username}")
    public ResponseEntity<UserResponseDto> updateUser(@PathVariable String username, @RequestBody @Valid UserUpdateDto userUpdates, Authentication authentication) {
        if (!authentication.getName().equals(username) && authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        AppUser updated = userService.updateUser(username, userUpdates);
        return ResponseEntity.ok(UserResponseDto.from(updated));
    }

    @DeleteMapping("/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable String username) {
        userService.deleteUser(username);
        return ResponseEntity.noContent().build();
    }
}
