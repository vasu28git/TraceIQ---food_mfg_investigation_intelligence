package com.taceiq.repository;

import com.taceiq.entity.InvestigationEvidenceAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvestigationEvidenceAssessmentRepository extends JpaRepository<InvestigationEvidenceAssessment, Long> {
    Optional<InvestigationEvidenceAssessment> findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(
            Long orgId, Long investigationId, Long canonicalEvidenceId);

    List<InvestigationEvidenceAssessment> findByOrganisationOrgIdAndInvestigationId(Long orgId, Long investigationId);
}