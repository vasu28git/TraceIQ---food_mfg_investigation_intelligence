package com.investigation.platform.audit.mapper;

import com.investigation.platform.audit.dto.response.AuditLogResponse;
import com.investigation.platform.audit.entity.AuditLog;
import org.springframework.stereotype.Component;

@Component
public class AuditLogMapper {

    public AuditLogResponse toResponse(AuditLog entity) {
        if (entity == null) {
            return null;
        }
        return AuditLogResponse.builder()
                .logId(entity.getLogId())
                .orgId(entity.getOrgId())
                .performedBy(entity.getPerformedBy())
                .action(entity.getAction())
                .entityType(entity.getEntityType())
                .entityId(entity.getEntityId())
                .details(entity.getDetails())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
