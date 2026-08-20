package com.investigation.platform.audit.service;

import com.investigation.platform.audit.dto.response.AuditLogResponse;
import com.investigation.platform.audit.enums.AuditAction;
import com.investigation.platform.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

public interface AuditLogService {

    void recordEvent(UUID orgId, UUID performedBy, AuditAction action, String entityType, String entityId, String details);

    void recordCurrentTenantEvent(UUID performedBy, AuditAction action, String entityType, String entityId, String details);

    AuditLogResponse getAuditLogById(UUID logId);

    PageResponse<AuditLogResponse> getAuditLogs(Pageable pageable);

    PageResponse<AuditLogResponse> getAuditLogsByAction(AuditAction action, Pageable pageable);

    PageResponse<AuditLogResponse> getAuditLogsByDateRange(Instant start, Instant end, Pageable pageable);
}
