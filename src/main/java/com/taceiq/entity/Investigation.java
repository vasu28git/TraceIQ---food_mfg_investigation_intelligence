package com.taceiq.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "investigation",
       uniqueConstraints = @UniqueConstraint(name = "uq_investigation_org_key", columnNames = {"org_id", "investigation_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Investigation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Organisation organisation;

    @Column(name = "investigation_key", nullable = false, columnDefinition = "TEXT")
    private String investigationKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Incident context — optional, Incident domain alias for Investigation persistence
    @Column(name = "batch_reference", columnDefinition = "TEXT")
    private String batchReference;

    @Column(name = "product_reference", columnDefinition = "TEXT")
    private String productReference;

    @Column(name = "order_reference", columnDefinition = "TEXT")
    private String orderReference;

    @Column(name = "incident_start")
    private Instant incidentStart;

    @Column(name = "incident_end")
    private Instant incidentEnd;

    @Column(nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String status = "DRAFT";

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
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = "DRAFT";
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
