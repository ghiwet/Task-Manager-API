package com.example.taskmanager.controller;

import com.example.taskmanager.dto.TaskCreateDto;
import com.example.taskmanager.dto.TaskDto;
import com.example.taskmanager.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<TaskDto> createTask(@RequestBody @Valid TaskCreateDto taskCreateDto, Authentication authentication) {
        TaskDto taskDto = taskService.createTask(taskCreateDto, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(taskDto);
    }

    @PutMapping(path = "{id}")
    public ResponseEntity<TaskDto> updateTask(@PathVariable(name = "id") Long id, @RequestBody TaskCreateDto taskCreateDto, Authentication authentication) {
        TaskDto taskDto = taskService.updateTask(id, authentication.getName(), taskCreateDto);
        return ResponseEntity.ok(taskDto);
    }

    @GetMapping(path = "{id}")
    public ResponseEntity<TaskDto> findTask(@PathVariable(name = "id") Long id, Authentication authentication) {
        TaskDto taskDto = taskService.findTask(id, authentication.getName());
        return ResponseEntity.ok(taskDto);
    }

    @GetMapping
    public ResponseEntity<Page<TaskDto>> findTasks(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        Page<TaskDto> taskDtos = taskService.findTasks(authentication.getName(), pageable);
        return ResponseEntity.ok(taskDtos);
    }

    @DeleteMapping(path = "{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable(name = "id") Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}
