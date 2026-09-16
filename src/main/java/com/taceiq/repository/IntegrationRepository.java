package com.taceiq.repository;

import com.taceiq.entity.Integration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IntegrationRepository extends JpaRepository<Integration, Long> {

    List<Integration> findByOrganisationOrgId(Long orgId);

    java.util.Optional<Integration> findByIdAndOrganisationOrgId(Long id, Long orgId);

    List<Integration> findByType(String type);

    List<Integration> findByStatus(String status);

    List<Integration> findByOrganisationOrgIdAndType(Long orgId, String type);

    java.util.Optional<Integration> findByName(String name);

    java.util.Optional<Integration> findByNameAndOrganisationOrgId(String name, Long orgId);

    boolean existsByNameAndOrganisationOrgId(String name, Long orgId);
}
