package com.example.taskmanager.audit;

import java.time.LocalDateTime;

public record AuditLogDto(Long id, String actor, AuditAction action, String targetType,
                           String targetId, String traceId, LocalDateTime createdAt) {

    public static AuditLogDto from(AuditLog entry) {
        return new AuditLogDto(entry.getId(), entry.getActor(), entry.getAction(),
                entry.getTargetType(), entry.getTargetId(), entry.getTraceId(), entry.getCreatedAt());
    }
}
