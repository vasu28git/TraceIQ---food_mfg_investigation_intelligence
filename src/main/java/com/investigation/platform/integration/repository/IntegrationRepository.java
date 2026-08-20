package com.investigation.platform.integration.repository;

import com.investigation.platform.integration.entity.Integration;
import com.investigation.platform.integration.enums.IntegrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IntegrationRepository extends JpaRepository<Integration, UUID> {

    Page<Integration> findByOrgId(UUID orgId, Pageable pageable);

    Optional<Integration> findByIntIdAndOrgId(UUID intId, UUID orgId);

    Optional<Integration> findByNameAndOrgId(String name, UUID orgId);

    boolean existsByNameAndOrgId(String name, UUID orgId);

    boolean existsByIntIdAndOrgId(UUID intId, UUID orgId);

    Page<Integration> findByOrgIdAndStatus(UUID orgId, IntegrationStatus status, Pageable pageable);

    void deleteByIntIdAndOrgId(UUID intId, UUID orgId);
}
