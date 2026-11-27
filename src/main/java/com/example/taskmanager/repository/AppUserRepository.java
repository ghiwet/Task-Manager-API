package com.example.taskmanager.repository;

import com.example.taskmanager.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);

    // Tenant-blind (V12 SECURITY DEFINER function); findByUsername only sees the caller's tenant.
    @Query(value = "SELECT username_taken(:username)", nativeQuery = true)
    boolean isUsernameTaken(String username);
}

