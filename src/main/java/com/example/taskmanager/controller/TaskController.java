package com.example.taskmanager.controller;

import com.example.taskmanager.dto.TaskCreateDto;
import com.example.taskmanager.dto.TaskDto;
import com.example.taskmanager.model.Task;
import com.example.taskmanager.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(path = "/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public TaskDto createTask(@RequestBody @Valid TaskCreateDto taskCreateDto) {
        Task task =  taskService.createTask(taskCreateDto);
        return taskService.convertEntityToDto(task);
    }

    @PutMapping(path = "{id}")
    public TaskDto updateTask(@PathVariable(name = "id")  Long id, @RequestBody TaskCreateDto taskCreateDto) {
        Task task =  taskService.findTaskById(id);
        task = taskService.updateTask(task, taskCreateDto);
        return taskService.convertEntityToDto(task);
    }

    @GetMapping(path = "{id}")
    public TaskDto findTask(@PathVariable(name = "id")  Long id) {
        Task task =  taskService.findTaskById(id);
        return taskService.convertEntityToDto(task);
    }

    @GetMapping
    public List<TaskDto> findTasks() {
        List<Task>  tasks = taskService.findTasks();
        return taskService.convertEntitiesToDtos(tasks);
    }

    @DeleteMapping(path = "{id}")
    public void deleteTask(@PathVariable(name = "id")  Long id) {
        Task task =  taskService.findTaskById(id);
        taskService.deleteTask(task);
    }

}
