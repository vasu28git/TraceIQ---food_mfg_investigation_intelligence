package com.investigation.platform.audit.controller;

import com.investigation.platform.audit.dto.response.AuditLogResponse;
import com.investigation.platform.audit.enums.AuditAction;
import com.investigation.platform.audit.service.AuditLogService;
import com.investigation.platform.common.dto.ApiResponse;
import com.investigation.platform.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "Endpoints for immutable tenant audit trail inspection")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping("/{id}")
    @Operation(summary = "Get audit log entry by ID within tenant")
    @PreAuthorize("hasAuthority('AUDIT_READ') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<AuditLogResponse>> getAuditLogById(@PathVariable("id") UUID id) {
        AuditLogResponse response = auditLogService.getAuditLogById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping
    @Operation(summary = "List audit logs within tenant (paginated, with optional filters)")
    @PreAuthorize("hasAuthority('AUDIT_READ') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> getAuditLogs(
            @RequestParam(name = "action", required = false) AuditAction action,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @PageableDefault(size = 20) Pageable pageable) {

        PageResponse<AuditLogResponse> response;
        if (action != null) {
            response = auditLogService.getAuditLogsByAction(action, pageable);
        } else if (startDate != null && endDate != null) {
            response = auditLogService.getAuditLogsByDateRange(startDate, endDate, pageable);
        } else {
            response = auditLogService.getAuditLogs(pageable);
        }
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
