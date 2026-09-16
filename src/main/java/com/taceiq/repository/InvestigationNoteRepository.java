package com.taceiq.repository;

import com.taceiq.entity.InvestigationNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvestigationNoteRepository extends JpaRepository<InvestigationNote, Long> {

    Page<InvestigationNote> findByOrganisationOrgIdAndInvestigationId(Long orgId, Long investigationId, Pageable pageable);

    Optional<InvestigationNote> findByIdAndOrganisationOrgId(Long id, Long orgId);

    Optional<InvestigationNote> findByIdAndOrganisationOrgIdAndInvestigationId(Long id, Long orgId, Long investigationId);
}
