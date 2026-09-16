package com.taceiq.repository;

import com.taceiq.entity.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfigurationRepository extends JpaRepository<Configuration, Long> {

    List<Configuration> findByOrganisationOrgId(Long orgId);

    Optional<Configuration> findByIdAndOrganisationOrgId(Long id, Long orgId);

    Optional<Configuration> findByDefinitionKeyAndOrganisationOrgId(String key, Long orgId);

    Optional<Configuration> findByDefinitionIdAndOrganisationOrgId(Long definitionId, Long orgId);

    boolean existsByDefinitionKeyAndOrganisationOrgId(String key, Long orgId);

    boolean existsByDefinitionIdAndOrganisationOrgId(Long definitionId, Long orgId);
}
