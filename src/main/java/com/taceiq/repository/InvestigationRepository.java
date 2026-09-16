package com.taceiq.repository;

import com.taceiq.entity.Investigation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvestigationRepository extends JpaRepository<Investigation, Long> {

    Optional<Investigation> findByIdAndOrganisationOrgId(Long id, Long orgId);

    List<Investigation> findAllByOrganisationOrgId(Long orgId);

    org.springframework.data.domain.Page<Investigation> findByOrganisationOrgId(Long orgId, org.springframework.data.domain.Pageable pageable);

    boolean existsByInvestigationKeyAndOrganisationOrgId(String investigationKey, Long orgId);

    Optional<Investigation> findByInvestigationKeyAndOrganisationOrgId(String investigationKey, Long orgId);
}
