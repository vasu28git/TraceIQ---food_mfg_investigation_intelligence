package com.taceiq.repository;

import com.taceiq.entity.Complaint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    Optional<Complaint> findByIdAndOrganisationOrgId(Long id, Long orgId);

    boolean existsByComplaintKeyAndOrganisationOrgId(String complaintKey, Long orgId);

    Page<Complaint> findByOrganisationOrgId(Long orgId, Pageable pageable);

    boolean existsByOrganisationOrgIdAndIntegrationIdAndExternalReference(Long orgId, Long integrationId, String externalReference);

    Optional<Complaint> findByOrganisationOrgIdAndIntegrationIdAndExternalReference(Long orgId, Long integrationId, String externalReference);

    Optional<Complaint> findByInvestigationIdAndOrganisationOrgId(Long investigationId, Long orgId);
}
