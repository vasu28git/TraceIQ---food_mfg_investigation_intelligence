package com.taceiq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "evidence_correlation_provenance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvidenceCorrelationProvenance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = false)
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canonical_evidence_id", nullable = false)
    private CanonicalEvidence canonicalEvidence;

    @Column(nullable = false)
    private String reason;

    @Column(name = "matched_field")
    private String matchedField;

    @Column(name = "matched_value", columnDefinition = "TEXT")
    private String matchedValue;

    @Column(name = "source_record_id", columnDefinition = "TEXT")
    private String sourceRecordId;

    @Column(name = "intermediate_entity_type")
    private String intermediateEntityType;

    @Column(name = "intermediate_entity_value", columnDefinition = "TEXT")
    private String intermediateEntityValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "connection_path", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private String connectionPath = "[]";

    @Column(name = "discovered_at", nullable = false)
    @Builder.Default
    private Instant discoveredAt = Instant.now();
}