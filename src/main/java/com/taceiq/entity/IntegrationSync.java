package com.taceiq.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "integration_sync")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationSync {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "users", "roles", "configurations", "integrations", "files"})
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Integration integration;

    @Column(nullable = false)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "external_cursor", columnDefinition = "TEXT")
    private String externalCursor;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "records_fetched", nullable = false)
    @Builder.Default
    private Integer recordsFetched = 0;

    @Column(name = "records_created", nullable = false)
    @Builder.Default
    private Integer recordsCreated = 0;

    @Column(name = "records_updated", nullable = false)
    @Builder.Default
    private Integer recordsUpdated = 0;

    @Column(name = "records_skipped", nullable = false)
    @Builder.Default
    private Integer recordsSkipped = 0;

    @Column(name = "records_failed", nullable = false)
    @Builder.Default
    private Integer recordsFailed = 0;

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String stats;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (this.startedAt == null) this.startedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = "PENDING";
        if (this.recordsFetched == null) this.recordsFetched = 0;
        if (this.recordsCreated == null) this.recordsCreated = 0;
        if (this.recordsUpdated == null) this.recordsUpdated = 0;
        if (this.recordsSkipped == null) this.recordsSkipped = 0;
        if (this.recordsFailed == null) this.recordsFailed = 0;
        if (this.retryCount == null) this.retryCount = 0;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
