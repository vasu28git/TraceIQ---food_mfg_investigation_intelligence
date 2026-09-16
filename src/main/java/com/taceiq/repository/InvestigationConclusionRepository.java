package com.taceiq.repository;

import com.taceiq.entity.InvestigationConclusion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvestigationConclusionRepository extends JpaRepository<InvestigationConclusion, Long> {
    Optional<InvestigationConclusion> findByOrganisationOrgIdAndInvestigationId(Long orgId, Long investigationId);
}
