package com.taceiq.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "complaint",
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_complaint_org_key", columnNames = {"org_id", "complaint_key"}),
           @UniqueConstraint(name = "uq_complaint_org_integration_external", columnNames = {"org_id", "integration_id", "external_reference"})
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Organisation organisation;

    @Column(name = "complaint_key", nullable = false, columnDefinition = "TEXT")
    private String complaintKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Investigation investigation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Integration integration;

    @Column(name = "source_type", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String sourceType = "MANUAL";

    @Column(name = "external_reference", columnDefinition = "TEXT")
    private String externalReference;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "batch_reference", columnDefinition = "TEXT")
    private String batchReference;

    @Column(name = "raised_at")
    private Instant raisedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (this.receivedAt == null) this.receivedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
        if (this.sourceType == null) this.sourceType = "MANUAL";
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
