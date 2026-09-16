package com.taceiq.repository;

import com.taceiq.entity.InvestigationAction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InvestigationActionRepository extends JpaRepository<InvestigationAction, Long> {
    List<InvestigationAction> findByOrganisationOrgIdAndInvestigationIdOrderByDueDateAscUpdatedAtDesc(Long orgId, Long investigationId);
    Optional<InvestigationAction> findByIdAndOrganisationOrgIdAndInvestigationId(Long id, Long orgId, Long investigationId);
}
