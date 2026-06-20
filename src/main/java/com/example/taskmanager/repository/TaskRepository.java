package com.example.taskmanager.repository;

import com.example.taskmanager.model.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    Page<Task> findByOwner(String owner, Pageable pageable);
    Optional<Task> findByIdAndOwner(Long id, String owner);
}
