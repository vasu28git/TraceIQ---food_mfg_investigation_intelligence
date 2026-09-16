package com.taceiq.repository;

import com.taceiq.entity.InvestigationConclusionFinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvestigationConclusionFindingRepository extends JpaRepository<InvestigationConclusionFinding, Long> {
    List<InvestigationConclusionFinding> findByOrganisationOrgIdAndConclusionIdOrderByIdAsc(Long orgId, Long conclusionId);
    void deleteByOrganisationOrgIdAndConclusionId(Long orgId, Long conclusionId);
}
