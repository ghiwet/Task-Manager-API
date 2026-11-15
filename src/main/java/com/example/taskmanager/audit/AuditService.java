package com.example.taskmanager.audit;

import com.example.taskmanager.tenant.TenantContext;
import io.micrometer.tracing.Tracer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Append-only audit trail for admin destructive actions (threat #2). record() runs inside the
// caller's own transaction so the audited change and the audit row commit atomically.
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final Tracer tracer;

    public AuditService(AuditLogRepository auditLogRepository, Tracer tracer) {
        this.auditLogRepository = auditLogRepository;
        this.tracer = tracer;
    }

    @Transactional
    public void record(AuditAction action, String targetType, String targetId) {
        AuditLog entry = new AuditLog();
        entry.setActor(SecurityContextHolder.getContext().getAuthentication().getName());
        entry.setTenantId(TenantContext.getTenantId());
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setTraceId(currentTraceId());
        auditLogRepository.save(entry);
    }

    private String currentTraceId() {
        var span = tracer.currentSpan();
        return span != null ? span.context().traceId() : null;
    }
}
