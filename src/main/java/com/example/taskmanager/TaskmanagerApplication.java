package com.example.taskmanager;

import com.example.taskmanager.enumration.Role;
import com.example.taskmanager.model.AppUser;
import com.example.taskmanager.repository.AppUserRepository;
import com.example.taskmanager.tenant.TenantContext;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

@SpringBootApplication
public class TaskmanagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(TaskmanagerApplication.class, args);
	}

	@Bean
	CommandLineRunner init(AppUserRepository repository, PasswordEncoder encoder) {
		return args -> {
			seedUser(repository, "user", encoder.encode("password"), Set.of(Role.ROLE_USER));
			seedUser(repository, "admin", encoder.encode("adminpass"), Set.of(Role.ROLE_USER, Role.ROLE_ADMIN));
		};
	}

	// Idempotent dev-user seeding. users is RLS-protected, so at startup (no tenant) findByUsername can't
	// see rows from another tenant — but username is globally unique, so a blind insert crashes. Catch it.
	private static void seedUser(AppUserRepository repository, String username, String encodedPassword, Set<Role> roles) {
		if (repository.findByUsername(username).isPresent()) {
			return;
		}
		try {
			AppUser user = new AppUser();
			user.setUsername(username);
			user.setPassword(encodedPassword);
			user.setRoles(roles);
			user.setTenantId(TenantContext.getTenantId());
			repository.save(user);
		} catch (DataIntegrityViolationException e) {
			// Already present (RLS may hide it from the current tenant context) — fine.
		}
	}
}
