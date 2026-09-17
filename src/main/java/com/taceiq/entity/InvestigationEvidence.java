package com.taceiq.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "investigation_evidence",
       uniqueConstraints = @UniqueConstraint(name = "uq_investigation_evidence", columnNames = {"org_id", "investigation_id", "canonical_evidence_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestigationEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Investigation investigation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canonical_evidence_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private CanonicalEvidence canonicalEvidence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User reviewedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "relevance")
    @Builder.Default
    private String relevance = "RELATED";

    @Column(name = "distance")
    @Builder.Default
    private Integer distance = 1;

    @Column(name = "discovery_method")
    @Builder.Default
    private String discoveryMethod = "NEO4J_GRAPH_TRAVERSAL";

    @Column(name = "discovery_path", columnDefinition = "jsonb")
    @Builder.Default
    private String discoveryPath = "[]";

    @Column(name = "discovery_reason", columnDefinition = "TEXT")
    private String discoveryReason;

    @Column(name = "review_status", nullable = false)
    @Builder.Default
    private String reviewStatus = "PENDING_REVIEW";

    @Column(name = "investigator_notes", columnDefinition = "TEXT")
    private String investigatorNotes;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
