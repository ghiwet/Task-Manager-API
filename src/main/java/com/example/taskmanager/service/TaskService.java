package com.example.taskmanager.service;

import com.example.taskmanager.dto.TaskCreateDto;
import com.example.taskmanager.dto.TaskDto;
import com.example.taskmanager.event.TaskEvent;
import com.example.taskmanager.event.TaskEventType;
import com.example.taskmanager.model.Task;
import com.example.taskmanager.repository.TaskRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaskService {
    private final TaskRepository taskRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TaskService(TaskRepository taskRepository, ApplicationEventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.eventPublisher = eventPublisher;
    }

    public Task createTask(TaskCreateDto taskCreateDto){
        Task task = new Task();
        task.setTitle(taskCreateDto.getTitle());
        task.setDescription(taskCreateDto.getDescription());
        task.setCompleted(taskCreateDto.isCompleted());
        Task savedTask = taskRepository.save(task);
        publishEvent(savedTask, TaskEventType.CREATED);
        return savedTask;
    }

    public Task updateTask(Task task, TaskCreateDto taskDto){
        boolean wasCompleted = task.isCompleted();

        if(taskDto.getTitle() != null) {
            task.setTitle(taskDto.getTitle());
        }
        if(taskDto.getDescription() != null) {
            task.setDescription(taskDto.getDescription());
        }
        task.setCompleted(taskDto.isCompleted());
        Task savedTask = taskRepository.save(task);

        TaskEventType eventType = (!wasCompleted && savedTask.isCompleted())
                ? TaskEventType.COMPLETED
                : TaskEventType.UPDATED;
        publishEvent(savedTask, eventType);
        return savedTask;
    }

    public List<Task> findTasks() {
        return taskRepository.findAll();
    }

    public Task findTaskById(Long id) {
        return taskRepository.findById(id).orElse(null);
    }

    public void deleteTask(Task task) {
        publishEvent(task, TaskEventType.DELETED);
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

    private void publishEvent(Task task, TaskEventType eventType) {
        TaskEvent event = TaskEvent.builder()
                .taskId(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .completed(task.isCompleted())
                .eventType(eventType)
                .timestamp(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()))
                .build();
        eventPublisher.publishEvent(event);
    }
}
