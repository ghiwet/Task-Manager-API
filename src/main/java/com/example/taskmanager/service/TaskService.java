package com.example.taskmanager.service;

import com.example.taskmanager.audit.AuditAction;
import com.example.taskmanager.audit.AuditService;
import com.example.taskmanager.dto.TaskCreateDto;
import com.example.taskmanager.dto.TaskDto;
import com.example.taskmanager.event.TaskEvent;
import com.example.taskmanager.event.TaskEventType;
import com.example.taskmanager.exception.TaskNotFoundException;
import com.example.taskmanager.model.Task;
import com.example.taskmanager.config.CacheConfig;
import com.example.taskmanager.config.KafkaConfig;
import com.example.taskmanager.outbox.OutboxEvent;
import com.example.taskmanager.outbox.OutboxRepository;
import com.example.taskmanager.repository.TaskRepository;
import com.example.taskmanager.tenant.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class TaskService {
    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    // SpEL for @Cacheable; see cacheKey().
    private static final String CACHE_KEY = "T(com.example.taskmanager.service.TaskService).cacheKey(#owner, #id)";

    private final TaskRepository taskRepository;
    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final MeterRegistry meterRegistry;
    private final AuditService auditService;
    private final CacheManager cacheManager;

    public TaskService(TaskRepository taskRepository, OutboxRepository outboxRepository,
                       JsonMapper jsonMapper, MeterRegistry meterRegistry, AuditService auditService,
                       CacheManager cacheManager) {
        this.taskRepository = taskRepository;
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
        this.meterRegistry = meterRegistry;
        this.auditService = auditService;
        this.cacheManager = cacheManager;
    }

    /**
     * Task cache key: {@code tenant:owner:id}. Cached reads never reach Postgres, so RLS can't scope them;
     * the tenant in the key does (threat #27).
     */
    public static String cacheKey(String owner, Long id) {
        return TenantContext.getTenantId() + ":" + owner + ":" + id;
    }

    @Transactional
    public TaskDto createTask(TaskCreateDto taskCreateDto, String owner) {
        return Timer.builder("task.operation.duration").tag("operation", "create").register(meterRegistry).record(() -> {
            Task task = new Task();
            task.setTitle(taskCreateDto.getTitle());
            task.setDescription(taskCreateDto.getDescription());
            task.setCompleted(taskCreateDto.isCompleted());
            task.setOwner(owner);
            task.setTenantId(TenantContext.getTenantId());
            Task savedTask = taskRepository.save(task);
            meterRegistry.counter("task.operations.total", "type", TaskEventType.CREATED.name()).increment();
            publishEvent(savedTask, TaskEventType.CREATED);
            return toDto(savedTask);
        });
    }

    @Transactional
    public TaskDto updateTask(Long id, String owner, TaskCreateDto taskDto) {
        return Timer.builder("task.operation.duration").tag("operation", "update").register(meterRegistry).record(() -> {
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
            meterRegistry.counter("task.operations.total", "type", eventType.name()).increment();
            publishEvent(savedTask, eventType);
            evictAfterCommit(savedTask.getOwner(), id);
            return toDto(savedTask);
        });
    }

    public Page<TaskDto> findTasks(String owner, Pageable pageable) {
        return Timer.builder("task.operation.duration").tag("operation", "findAll").register(meterRegistry).record(() ->
                taskRepository.findByOwner(owner, pageable).map(this::toDto)
        );
    }

    // Admin view: every task in the caller's tenant (RLS-scoped), any owner.
    public Page<TaskDto> findAllTasks(Pageable pageable) {
        return Timer.builder("task.operation.duration").tag("operation", "findAllAdmin").register(meterRegistry).record(() ->
                taskRepository.findAll(pageable).map(this::toDto)
        );
    }

    @Cacheable(cacheNames = CacheConfig.TASKS_CACHE, key = CACHE_KEY)
    public TaskDto findTask(Long id, String owner) {
        return Timer.builder("task.operation.duration").tag("operation", "findOne").register(meterRegistry).record(() -> {
            Task task = taskRepository.findByIdAndOwner(id, owner)
                    .orElseThrow(() -> new TaskNotFoundException("Task with ID " + id + " not found"));
            return toDto(task);
        });
    }

    /**
     * Deletes a task; admins may delete any task in their tenant. The cache entry is evicted under the
     * task's actual owner, not the caller, so an admin delete clears the owner's copy (threat #27).
     */
    @Transactional
    public void deleteTask(Long id, String owner, boolean isAdmin) {
        Timer.builder("task.operation.duration").tag("operation", "delete").register(meterRegistry).record(() -> {
            Task task = isAdmin
                    ? taskRepository.findById(id)
                            .orElseThrow(() -> new TaskNotFoundException("Task with ID " + id + " not found"))
                    : taskRepository.findByIdAndOwner(id, owner)
                            .orElseThrow(() -> new TaskNotFoundException("Task with ID " + id + " not found"));
            meterRegistry.counter("task.operations.total", "type", TaskEventType.DELETED.name()).increment();
            publishEvent(task, TaskEventType.DELETED);
            // Audit only cross-user deletes.
            if (isAdmin && !task.getOwner().equals(owner)) {
                auditService.record(AuditAction.TASK_DELETED, "task", String.valueOf(id));
            }
            taskRepository.delete(task);
            evictAfterCommit(task.getOwner(), id);
        });
    }

    /**
     * Evicts the task's cache entry once the surrounding transaction commits. Evicting before commit lets a
     * concurrent read re-cache the old row, which then stays stale until the TTL.
     */
    private void evictAfterCommit(String owner, Long id) {
        String key = cacheKey(owner, id);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(key);
                }
            });
        } else {
            evict(key);
        }
    }

    // Fails open like the annotated cache ops: a Redis outage mustn't fail the write.
    private void evict(String key) {
        try {
            Cache cache = cacheManager.getCache(CacheConfig.TASKS_CACHE);
            if (cache != null) {
                cache.evict(key);
            }
        } catch (RuntimeException e) {
            log.warn("Failed to evict task cache entry {}; it expires with the TTL", key, e);
        }
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

    // Staged in the outbox in the same transaction, so task change and event commit atomically.
    private void publishEvent(Task task, TaskEventType eventType) {
        TaskEvent event = TaskEvent.builder()
                .taskId(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .completed(task.isCompleted())
                .owner(task.getOwner())
                .tenantId(task.getTenantId())
                .eventType(eventType)
                .timestamp(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()))
                .build();
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateType("task")
                .aggregateId(String.valueOf(task.getId()))
                .eventType(eventType.name())
                .topic(KafkaConfig.TASK_EVENTS_TOPIC)
                .payload(jsonMapper.writeValueAsString(event))
                .build();
        outboxRepository.save(outboxEvent);
    }
}
