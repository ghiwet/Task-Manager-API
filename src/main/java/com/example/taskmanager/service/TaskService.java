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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class TaskService {
    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    // SpEL for the task cache key; see cacheKey(). Shared by @Cacheable and @CacheEvict.
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
     * Task cache key: {@code tenant:owner:id}. Cached reads are served from Redis without touching
     * Postgres, so RLS can't scope them; the tenant in the key does (threat #27). Without it, the same
     * owner name in two tenants would share entries, and a user moved to another tenant would keep
     * being served the old tenant's tasks until the TTL expired.
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
    // Safe to key on the caller: update only ever loads the caller's own task (findByIdAndOwner).
    @CacheEvict(cacheNames = CacheConfig.TASKS_CACHE, key = CACHE_KEY)
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
            return toDto(savedTask);
        });
    }

    public Page<TaskDto> findTasks(String owner, Pageable pageable) {
        return Timer.builder("task.operation.duration").tag("operation", "findAll").register(meterRegistry).record(() ->
                taskRepository.findByOwner(owner, pageable).map(this::toDto)
        );
    }

    // Every task the caller's tenant can see (RLS-scoped), regardless of owner — for admins.
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

    // Evicted programmatically, not via @CacheEvict: an admin can delete another user's task, and the
    // cached entry lives under the task's owner, which is only known after the lookup. Keying on the
    // caller (as before) evicted the admin's key and left the owner's stale copy readable (threat #27).
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
            // Audit only the cross-user case: an admin deleting their own task isn't the risk here.
            if (isAdmin && !task.getOwner().equals(owner)) {
                auditService.record(AuditAction.TASK_DELETED, "task", String.valueOf(id));
            }
            taskRepository.delete(task);
            evictCachedTask(task.getOwner(), id);
        });
    }

    // Fail open like the annotated cache operations (LoggingCacheErrorHandler only covers those):
    // a Redis outage must not fail the delete. The entry still expires with the TTL.
    private void evictCachedTask(String owner, Long id) {
        String key = cacheKey(owner, id);
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

    // Stage the event in the outbox within the same transaction as the task change, so the two
    // commit atomically; the OutboxRelay publishes it to Kafka afterwards (no dual-write loss).
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
