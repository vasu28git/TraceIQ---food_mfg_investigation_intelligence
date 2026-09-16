package com.taceiq.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "investigation_conclusion",
       uniqueConstraints = @UniqueConstraint(name = "uq_investigation_conclusion", columnNames = {"org_id", "investigation_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InvestigationConclusion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "org_id", nullable = false)
    private Organisation organisation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @Column(nullable = false) @Builder.Default private String lifecycle = "DRAFT";
    @Column(nullable = false) private String outcome;
    @Column(nullable = false, columnDefinition = "TEXT") private String summary;
    @Column(columnDefinition = "TEXT") private String confidence;
    @Column(name = "investigator_reasoning", columnDefinition = "TEXT") private String investigatorReasoning;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by_user_id") private User createdBy;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "updated_by_user_id") private User updatedBy;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Column(name = "finalized_at") private Instant finalizedAt;

    @PrePersist void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; if (lifecycle == null) lifecycle = "DRAFT"; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
}
