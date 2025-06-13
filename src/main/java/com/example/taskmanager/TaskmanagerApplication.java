package com.example.taskmanager;

import com.example.taskmanager.enumration.Role;
import com.example.taskmanager.model.AppUser;
import com.example.taskmanager.repository.AppUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
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
			if (repository.findByUsername("user").isEmpty()) {
				AppUser user = new AppUser();
				user.setUsername("user");
				user.setPassword(encoder.encode("password"));
				user.setRoles(Set.of(Role.ROLE_USER));
				repository.save(user);
			}

			if (repository.findByUsername("admin").isEmpty()) {
				AppUser admin = new AppUser();
				admin.setUsername("admin");
				admin.setPassword(encoder.encode("adminpass"));
				admin.setRoles(Set.of(Role.ROLE_USER, Role.ROLE_ADMIN));
				repository.save(admin);
			}
		};
	}

}
