package com.investigation.platform.audit.repository;

import com.investigation.platform.audit.entity.AuditLog;
import com.investigation.platform.audit.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByOrgId(UUID orgId, Pageable pageable);

    Optional<AuditLog> findByLogIdAndOrgId(UUID logId, UUID orgId);

    Page<AuditLog> findByOrgIdAndAction(UUID orgId, AuditAction action, Pageable pageable);

    Page<AuditLog> findByOrgIdAndPerformedBy(UUID orgId, UUID performedBy, Pageable pageable);

    Page<AuditLog> findByOrgIdAndCreatedAtBetween(UUID orgId, Instant start, Instant end, Pageable pageable);
}
