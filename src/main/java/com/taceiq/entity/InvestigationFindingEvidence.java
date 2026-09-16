package com.taceiq.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "investigation_finding_evidence",
       uniqueConstraints = @UniqueConstraint(name = "uq_finding_evidence", columnNames = {"org_id", "finding_id", "canonical_evidence_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InvestigationFindingEvidence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "org_id", nullable = false) private Organisation organisation;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "investigation_id", nullable = false) private Investigation investigation;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "finding_id", nullable = false) private InvestigationFinding finding;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "canonical_evidence_id", nullable = false) private CanonicalEvidence canonicalEvidence;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by_user_id") private User createdBy;
    @Column(name = "relationship_type", nullable = false) @Builder.Default private String relationshipType = "SUPPORTING";
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); if (relationshipType == null) relationshipType = "SUPPORTING"; }
}