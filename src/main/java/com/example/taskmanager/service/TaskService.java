package com.example.taskmanager.service;

import com.example.taskmanager.dto.TaskCreateDto;
import com.example.taskmanager.dto.TaskDto;
import com.example.taskmanager.event.TaskEvent;
import com.example.taskmanager.event.TaskEventType;
import com.example.taskmanager.exception.TaskNotFoundException;
import com.example.taskmanager.model.Task;
import com.example.taskmanager.repository.TaskRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class TaskService {
    private final TaskRepository taskRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TaskService(TaskRepository taskRepository, ApplicationEventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.eventPublisher = eventPublisher;
    }

    public TaskDto createTask(TaskCreateDto taskCreateDto, String owner) {
        Task task = new Task();
        task.setTitle(taskCreateDto.getTitle());
        task.setDescription(taskCreateDto.getDescription());
        task.setCompleted(taskCreateDto.isCompleted());
        task.setOwner(owner);
        Task savedTask = taskRepository.save(task);
        publishEvent(savedTask, TaskEventType.CREATED);
        return toDto(savedTask);
    }

    public TaskDto updateTask(Long id, String owner, TaskCreateDto taskDto) {
        Task task = taskRepository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new TaskNotFoundException("Task with ID " + id + " not found"));

        boolean wasCompleted = task.isCompleted();

        if (taskDto.getTitle() != null) {
            task.setTitle(taskDto.getTitle());
        }
        if (taskDto.getDescription() != null) {
            task.setDescription(taskDto.getDescription());
        }
        task.setCompleted(taskDto.isCompleted());
        Task savedTask = taskRepository.save(task);

        TaskEventType eventType = (!wasCompleted && savedTask.isCompleted())
                ? TaskEventType.COMPLETED
                : TaskEventType.UPDATED;
        publishEvent(savedTask, eventType);
        return toDto(savedTask);
    }

    public Page<TaskDto> findTasks(String owner, Pageable pageable) {
        return taskRepository.findByOwner(owner, pageable).map(this::toDto);
    }

    public TaskDto findTask(Long id, String owner) {
        Task task = taskRepository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new TaskNotFoundException("Task with ID " + id + " not found"));
        return toDto(task);
    }

    public Task findTaskById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task with ID " + id + " not found"));
    }

    public void deleteTask(Long id) {
        Task task = findTaskById(id);
        publishEvent(task, TaskEventType.DELETED);
        taskRepository.delete(task);
    }

    private TaskDto toDto(Task task) {
        TaskDto dto = new TaskDto();
        dto.setId(task.getId());
        dto.setVersion(task.getVersion());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setCompleted(task.isCompleted());
        dto.setOwner(task.getOwner());
        dto.setCreateAt(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(task.getCreatedAt()));
        dto.setUpdatedAt(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(task.getUpdatedAt()));
        return dto;
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
