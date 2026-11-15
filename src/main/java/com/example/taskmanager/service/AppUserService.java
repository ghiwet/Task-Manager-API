package com.example.taskmanager.service;

import com.example.taskmanager.audit.AuditAction;
import com.example.taskmanager.audit.AuditService;
import com.example.taskmanager.dto.UserUpdateDto;
import com.example.taskmanager.enumration.Role;
import com.example.taskmanager.exception.AppUserNotFoundException;
import com.example.taskmanager.model.AppUser;
import com.example.taskmanager.repository.AppUserRepository;
import com.example.taskmanager.tenant.TenantContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class AppUserService implements UserDetailsService {

    private final AppUserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final AuditService auditService;

    public AppUserService(AppUserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    // Load user for authentication
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.name()))
                .toList();

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                authorities
        );
    }

    public AppUser findUserByUserName(String userName) {
        return userRepository.findByUsername(userName).orElse(null);
    }

    public AppUser registerNewUser(AppUser newUser) {
        if (userRepository.findByUsername(newUser.getUsername()).isPresent()) {
            throw new IllegalStateException("Username already taken");
        }

        newUser.setPassword(passwordEncoder.encode(newUser.getPassword()));
        newUser.setTenantId(TenantContext.getTenantId());

        // Optionally assign default roles if none provided
        if (newUser.getRoles() == null || newUser.getRoles().isEmpty()) {
            newUser.setRoles(Set.of(Role.ROLE_USER));
        }

        return userRepository.save(newUser);
    }

    public List<AppUser> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public AppUser updateUser(String username, UserUpdateDto updates, String actingUser) {
        AppUser existingUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppUserNotFoundException("Username doesn't exist"));

        // Only the password is updatable; roles are never bound from the body, so a user
        // can't grant themselves ROLE_ADMIN.
        if (updates.password() != null) {
            existingUser.setPassword(passwordEncoder.encode(updates.password()));
            // Audit only the cross-user case: an admin resetting someone else's password.
            if (!actingUser.equals(username)) {
                auditService.record(AuditAction.PASSWORD_RESET, "user", username);
            }
        }
        return userRepository.save(existingUser);
    }

    @Transactional
    public void deleteUser(String username) {
        if (userRepository.findByUsername(username).isEmpty()) {
            throw new AppUserNotFoundException("Username doesn't exist");
        }
        AppUser existingUser = userRepository.findByUsername(username).get();
        auditService.record(AuditAction.USER_DELETED, "user", username);
        userRepository.delete(existingUser);
    }

    public PasswordEncoder passwordEncoder() {
        return passwordEncoder;
    }
}