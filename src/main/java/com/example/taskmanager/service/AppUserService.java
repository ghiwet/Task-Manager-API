package com.example.taskmanager.service;

import com.example.taskmanager.enumration.Role;
import com.example.taskmanager.exception.AppUserNotFoundException;
import com.example.taskmanager.model.AppUser;
import com.example.taskmanager.repository.AppUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class AppUserService implements UserDetailsService {

    private final AppUserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    public AppUserService(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

        // Optionally assign default roles if none provided
        if (newUser.getRoles() == null || newUser.getRoles().isEmpty()) {
            newUser.setRoles(Set.of(Role.ROLE_USER));
        }

        return userRepository.save(newUser);
    }

    public List<AppUser> getAllUsers() {
        return userRepository.findAll();
    }

    public AppUser updateUser(String username, AppUser user) {
        if (userRepository.findByUsername(username).isEmpty()) {
            throw new AppUserNotFoundException("Username doesn't exist");
        }
        AppUser existingUser = userRepository.findByUsername(username).get();

        if(user.getPassword() != null) {
            existingUser.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        if(user.getRoles() != null && !user.getRoles().isEmpty()) {
            existingUser.setRoles(user.getRoles());
        }
        return userRepository.save(existingUser);

    }

    public void deleteUser(String username) {
        if (userRepository.findByUsername(username).isEmpty()) {
            throw new AppUserNotFoundException("Username doesn't exist");
        }
        AppUser existingUser = userRepository.findByUsername(username).get();
        userRepository.delete(existingUser);
    }

    public PasswordEncoder passwordEncoder() {
        return passwordEncoder;
    }
}