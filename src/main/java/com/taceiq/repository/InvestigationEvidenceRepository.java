package com.taceiq.repository;

import com.taceiq.entity.InvestigationEvidence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface InvestigationEvidenceRepository extends JpaRepository<InvestigationEvidence, Long> {

    Optional<InvestigationEvidence> findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(Long orgId, Long investigationId, Long canonicalEvidenceId);

    Optional<InvestigationEvidence> findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceExternalId(Long orgId, Long investigationId, String externalId);

    @EntityGraph(attributePaths = {"canonicalEvidence", "canonicalEvidence.organisation"})
    Page<InvestigationEvidence> findByOrganisationOrgIdAndInvestigationId(Long orgId, Long investigationId, Pageable pageable);

    long countByOrganisationOrgIdAndInvestigationId(Long orgId, Long investigationId);

    boolean existsByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(Long orgId, Long investigationId, Long canonicalEvidenceId);

    @EntityGraph(attributePaths = {"organisation", "investigation", "canonicalEvidence", "canonicalEvidence.organisation"})
    List<InvestigationEvidence> findByOrganisationOrgId(Long orgId);
}
