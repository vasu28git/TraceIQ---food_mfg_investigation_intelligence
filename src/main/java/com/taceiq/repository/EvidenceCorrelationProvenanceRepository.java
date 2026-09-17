package com.taceiq.repository;

import com.taceiq.entity.EvidenceCorrelationProvenance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvidenceCorrelationProvenanceRepository extends JpaRepository<EvidenceCorrelationProvenance, Long> {

    List<EvidenceCorrelationProvenance> findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(
            Long orgId, Long investigationId, Long canonicalEvidenceId);

    @EntityGraph(attributePaths = {"canonicalEvidence"})
    List<EvidenceCorrelationProvenance> findByOrganisationOrgIdAndInvestigationId(Long orgId, Long investigationId);
}