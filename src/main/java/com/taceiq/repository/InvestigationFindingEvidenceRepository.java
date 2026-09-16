package com.taceiq.repository;

import com.taceiq.entity.InvestigationFindingEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InvestigationFindingEvidenceRepository extends JpaRepository<InvestigationFindingEvidence, Long> {
    boolean existsByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(Long orgId, Long investigationId, Long canonicalEvidenceId);
    List<InvestigationFindingEvidence> findByOrganisationOrgIdAndInvestigationIdAndFindingId(Long orgId, Long investigationId, Long findingId);
    void deleteByOrganisationOrgIdAndInvestigationIdAndFindingId(Long orgId, Long investigationId, Long findingId);
}