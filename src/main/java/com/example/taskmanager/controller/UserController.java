package com.example.taskmanager.controller;

import com.example.taskmanager.exception.AppUserNotFoundException;
import com.example.taskmanager.model.AppUser;
import com.example.taskmanager.service.AppUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping(path = "/api/users")
public class UserController {
    private final AppUserService userService;

    public UserController(AppUserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<AppUser> getCurrentUser(Authentication authentication) {
        String username = authentication.getName();
        AppUser user = userService.findUserByUserName(username);
        if(Objects.isNull(user)) {
            throw new AppUserNotFoundException("User not found");
        }
        return ResponseEntity.ok(user);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AppUser>> getAllUsers() {
        List<AppUser> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @PostMapping("/register")
    public ResponseEntity<AppUser> registerUser(@RequestBody AppUser newUser) {
        AppUser created = userService.registerNewUser(newUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{username}")
    public ResponseEntity<AppUser> updateUser(@PathVariable String username, @RequestBody AppUser userUpdates, Authentication authentication) {
        if (!authentication.getName().equals(username) && !authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        AppUser updated = userService.updateUser(username, userUpdates);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable String username) {
        userService.deleteUser(username);
        return ResponseEntity.noContent().build();
    }
}
