package com.taceiq.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "canonical_evidence",
       uniqueConstraints = @UniqueConstraint(name = "uq_evidence_org_integ_external",
               columnNames = {"org_id", "integration_id", "external_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CanonicalEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "users", "roles", "configurations", "integrations", "files"})
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Integration integration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sync_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private IntegrationSync sync;

    // Incident is the domain alias for Investigation; incident_id FK points to investigation(id).
    // Nullable in Phase 1 for backward compatibility — legacy evidence remains with NULL.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Investigation incident;

    @Column(name = "external_id", nullable = false, columnDefinition = "TEXT")
    private String externalId;

    @Column(name = "case_id", columnDefinition = "TEXT")
    private String caseId;

    @Column(name = "actor_id", columnDefinition = "TEXT")
    private String actorId;

    @Column(name = "parent_id", columnDefinition = "TEXT")
    private String parentId;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(name = "source_type", columnDefinition = "TEXT")
    private String sourceType;

    @Column(columnDefinition = "TEXT")
    private String status;

    @Column(name = "source_created_at")
    private Instant sourceCreatedAt;

    @Column(name = "source_updated_at")
    private Instant sourceUpdatedAt;

    @Column(name = "content_hash", nullable = false, columnDefinition = "TEXT")
    private String contentHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "normalized_payload", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private String normalizedPayload = "{}";

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (this.firstSeenAt == null) this.firstSeenAt = now;
        if (this.lastSeenAt == null) this.lastSeenAt = now;
        this.createdAt = now;
        this.updatedAt = now;
        if (this.isDeleted == null) this.isDeleted = false;
        if (this.normalizedPayload == null) this.normalizedPayload = "{}";
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
