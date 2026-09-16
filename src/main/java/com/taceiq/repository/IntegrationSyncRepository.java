package com.taceiq.repository;

import com.taceiq.entity.IntegrationSync;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IntegrationSyncRepository extends JpaRepository<IntegrationSync, Long> {

    Optional<IntegrationSync> findByIdAndOrganisationOrgId(Long id, Long orgId);

    List<IntegrationSync> findAllByOrganisationOrgId(Long orgId);

    List<IntegrationSync> findByIntegrationIdAndOrganisationOrgId(Long integrationId, Long orgId);

    List<IntegrationSync> findByOrganisationOrgIdOrderByStartedAtDesc(Long orgId);

    List<IntegrationSync> findByStatusAndOrganisationOrgId(String status, Long orgId);

    java.util.Optional<IntegrationSync> findTopByOrganisationOrgIdAndIntegrationIdAndStatusOrderByCompletedAtDesc(Long orgId, Long integrationId, String status);
}
