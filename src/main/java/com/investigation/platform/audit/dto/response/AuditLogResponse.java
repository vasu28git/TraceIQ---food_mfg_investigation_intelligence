package com.investigation.platform.audit.dto.response;

import com.investigation.platform.audit.enums.AuditAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {

    private UUID logId;
    private UUID orgId;
    private UUID performedBy;
    private AuditAction action;
    private String entityType;
    private String entityId;
    private String details;
    private Instant createdAt;
}
