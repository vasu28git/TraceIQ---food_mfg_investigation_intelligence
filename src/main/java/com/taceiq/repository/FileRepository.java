package com.taceiq.repository;

import com.taceiq.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<File, Long> {

    List<File> findByIntegrationId(Long integrationId);

    List<File> findByIntegrationOrganisationOrgId(Long orgId);

    List<File> findByOrganisationOrgId(Long orgId);

    Optional<File> findByIdAndOrganisationOrgId(Long id, Long orgId);

    List<File> findByIntegrationIdAndOrganisationOrgId(Long integrationId, Long orgId);

    List<File> findByIntegrationIdAndIntegrationOrganisationOrgId(Long integrationId, Long orgId);

    List<File> findByStatus(String status);

    List<File> findByStatusAndOrganisationOrgId(String status, Long orgId);

    long countByIntegrationIdAndOrganisationOrgId(Long integrationId, Long orgId);
}
