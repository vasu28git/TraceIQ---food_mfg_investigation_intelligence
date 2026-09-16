package com.taceiq.repository;

import com.taceiq.entity.InvestigationFinding;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InvestigationFindingRepository extends JpaRepository<InvestigationFinding, Long> {
    List<InvestigationFinding> findByOrganisationOrgIdAndInvestigationIdOrderByUpdatedAtDesc(Long orgId, Long investigationId);
    Optional<InvestigationFinding> findByIdAndOrganisationOrgIdAndInvestigationId(Long id, Long orgId, Long investigationId);
}