package com.taceiq.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "investigation_evidence_assessment",
       uniqueConstraints = @UniqueConstraint(name = "uq_evidence_assessment", columnNames = {"org_id", "investigation_id", "canonical_evidence_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestigationEvidenceAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id", nullable = false)
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "canonical_evidence_id", nullable = false)
    private CanonicalEvidence canonicalEvidence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "review_status", nullable = false)
    @Builder.Default
    private String reviewStatus = "PENDING_REVIEW";

    private String relevance;
    private String importance;
    private String assessment;

    @Column(name = "investigator_notes", columnDefinition = "TEXT")
    private String investigatorNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (reviewStatus == null) reviewStatus = "PENDING_REVIEW";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}