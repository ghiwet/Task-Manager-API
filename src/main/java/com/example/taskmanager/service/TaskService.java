package com.example.taskmanager.service;

import com.example.taskmanager.dto.TaskCreateDto;
import com.example.taskmanager.dto.TaskDto;
import com.example.taskmanager.model.Task;
import com.example.taskmanager.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaskService {
    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Task createTask(TaskCreateDto taskCreateDto){
        Task task = new Task();
        task.setTitle(taskCreateDto.getTitle());
        task.setDescription(taskCreateDto.getDescription());
        task.setCompleted(taskCreateDto.isCompleted());
        return taskRepository.save(task);
    }

    public Task updateTask(Task task, TaskCreateDto taskDto){
        if(taskDto.getTitle() != null) {
            task.setTitle(taskDto.getTitle());
        }
        if(taskDto.getDescription() != null) {
            task.setDescription(taskDto.getDescription());
        }
        task.setCompleted(taskDto.isCompleted());
        return taskRepository.save(task);
    }

    public List<Task> findTasks() {
        return taskRepository.findAll();
    }

    public Task findTaskById(Long id) {
        return taskRepository.findById(id).orElse(new Task());
    }

    public void deleteTask(Task task) {

        taskRepository.delete(task);
    }

    public TaskDto convertEntityToDto(Task task) {
        TaskDto dto = new TaskDto();
        dto.setId(task.getId());
        dto.setVersion(task.getVersion());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setCompleted(task.isCompleted());
        dto.setCreateAt(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(task.getCreatedAt()));
        dto.setUpdatedAt(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(task.getUpdatedAt()));

        return dto;
    }

    public List<TaskDto> convertEntitiesToDtos(List<Task> tasks) {
        List<TaskDto> dtos = new ArrayList<>();
        tasks.forEach(task -> dtos.add(convertEntityToDto(task)));
        return dtos;
    }
}
