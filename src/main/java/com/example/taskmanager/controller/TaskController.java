package com.example.taskmanager.controller;

import com.example.taskmanager.dto.TaskCreateDto;
import com.example.taskmanager.dto.TaskDto;
import com.example.taskmanager.exception.TaskNotFoundException;
import com.example.taskmanager.model.Task;
import com.example.taskmanager.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping(path = "/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<TaskDto> createTask(@RequestBody @Valid TaskCreateDto taskCreateDto) {
        Task task = taskService.createTask(taskCreateDto);
        TaskDto taskDto = taskService.convertEntityToDto(task);

        return ResponseEntity.status(HttpStatus.CREATED).body(taskDto);
    }

    @PutMapping(path = "{id}")
    public ResponseEntity<TaskDto> updateTask(@PathVariable(name = "id")  Long id, @RequestBody TaskCreateDto taskCreateDto) {
        Task task =  taskService.findTaskById(id);
        if(Objects.isNull(task)){
            throw new TaskNotFoundException("Task with ID " + id + " not found");
        }
        task = taskService.updateTask(task, taskCreateDto);
        TaskDto taskDto = taskService.convertEntityToDto(task);

        return ResponseEntity.ok().body(taskDto);
    }

    @GetMapping(path = "{id}")
    public ResponseEntity<TaskDto>  findTask(@PathVariable(name = "id")  Long id) {
        Task task =  taskService.findTaskById(id);
        if(Objects.isNull(task)){
            throw new TaskNotFoundException("Task with ID " + id + " not found");
        }
        TaskDto taskDto = taskService.convertEntityToDto(task);

        return ResponseEntity.ok().body(taskDto);
    }

    @GetMapping
    public ResponseEntity<List<TaskDto>> findTasks() {
        List<Task>  tasks = taskService.findTasks();
        List<TaskDto> taskDtos = taskService.convertEntitiesToDtos(tasks);
        return ResponseEntity.ok().body(taskDtos);
    }

    @DeleteMapping(path = "{id}")
    public void deleteTask(@PathVariable(name = "id")  Long id) {
        Task task =  taskService.findTaskById(id);
        if(Objects.isNull(task)){
            throw new TaskNotFoundException("Task with ID " + id + " not found");
        }
        taskService.deleteTask(task);
    }

}
