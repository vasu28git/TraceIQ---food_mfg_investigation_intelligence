package com.investigation.platform.audit.service.impl;

import com.investigation.platform.audit.dto.response.AuditLogResponse;
import com.investigation.platform.audit.entity.AuditLog;
import com.investigation.platform.audit.enums.AuditAction;
import com.investigation.platform.audit.mapper.AuditLogMapper;
import com.investigation.platform.audit.repository.AuditLogRepository;
import com.investigation.platform.audit.service.AuditLogService;
import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.exception.ResourceNotFoundException;
import com.investigation.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEvent(UUID orgId, UUID performedBy, AuditAction action, String entityType, String entityId, String details) {
        try {
            AuditLog logEntry = AuditLog.builder()
                    .orgId(orgId)
                    .performedBy(performedBy)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .details(details)
                    .build();

            auditLogRepository.save(logEntry);
            log.debug("Audit event recorded: {} on {} for org {}", action, entityType, orgId);
        } catch (Exception e) {
            log.error("Failed to record audit event: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCurrentTenantEvent(UUID performedBy, AuditAction action, String entityType, String entityId, String details) {
        UUID orgId = TenantContext.getTenantId();
        if (orgId != null) {
            recordEvent(orgId, performedBy, action, entityType, entityId, details);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AuditLogResponse getAuditLogById(UUID logId) {
        UUID orgId = TenantContext.getRequiredTenantId();
        AuditLog auditLog = auditLogRepository.findByLogIdAndOrgId(logId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditLog", "logId", logId));
        return auditLogMapper.toResponse(auditLog);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAuditLogs(Pageable pageable) {
        UUID orgId = TenantContext.getRequiredTenantId();
        Page<AuditLog> page = auditLogRepository.findByOrgId(orgId, pageable);
        return PageResponse.from(page.map(auditLogMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAuditLogsByAction(AuditAction action, Pageable pageable) {
        UUID orgId = TenantContext.getRequiredTenantId();
        Page<AuditLog> page = auditLogRepository.findByOrgIdAndAction(orgId, action, pageable);
        return PageResponse.from(page.map(auditLogMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAuditLogsByDateRange(Instant start, Instant end, Pageable pageable) {
        UUID orgId = TenantContext.getRequiredTenantId();
        Page<AuditLog> page = auditLogRepository.findByOrgIdAndCreatedAtBetween(orgId, start, end, pageable);
        return PageResponse.from(page.map(auditLogMapper::toResponse));
    }
}
