package com.taceiq.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "investigation_conclusion_finding",
       uniqueConstraints = @UniqueConstraint(name = "uq_conclusion_finding", columnNames = {"org_id", "conclusion_id", "finding_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InvestigationConclusionFinding {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "org_id", nullable = false) private Organisation organisation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "investigation_id", nullable = false) private Investigation investigation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "conclusion_id", nullable = false) private InvestigationConclusion conclusion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "finding_id", nullable = false) private InvestigationFinding finding;
    @Column(name = "relationship_type", nullable = false) private String relationshipType;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by_user_id") private User createdBy;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
