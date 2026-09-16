package com.taceiq.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "investigation_finding")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InvestigationFinding {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "org_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "investigation_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Investigation investigation;

    @Column(nullable = false, columnDefinition = "TEXT") private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(columnDefinition = "TEXT") private String conclusion;
    @Column(nullable = false) @Builder.Default private String status = "OPEN";
    @Column(columnDefinition = "TEXT") private String category;
    @Column(columnDefinition = "TEXT") private String confidence;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by_user_id")
    private User createdBy;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    @PrePersist void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; if (status == null) status = "OPEN"; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
}