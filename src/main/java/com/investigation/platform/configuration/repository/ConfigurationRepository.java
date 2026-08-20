package com.investigation.platform.configuration.repository;

import com.investigation.platform.configuration.entity.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConfigurationRepository extends JpaRepository<Configuration, UUID> {

    Page<Configuration> findByOrgId(UUID orgId, Pageable pageable);

    Optional<Configuration> findByConfigIdAndOrgId(UUID configId, UUID orgId);

    Optional<Configuration> findByOrgIdAndConfigKey(UUID orgId, String configKey);

    boolean existsByOrgIdAndConfigKey(UUID orgId, String configKey);

    void deleteByOrgIdAndConfigKey(UUID orgId, String configKey);
}
