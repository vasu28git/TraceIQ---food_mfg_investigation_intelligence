package com.taceiq.repository;

import com.taceiq.entity.ConfigurationDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfigurationDefinitionRepository extends JpaRepository<ConfigurationDefinition, Long> {

    Optional<ConfigurationDefinition> findByKey(String key);

    boolean existsByKey(String key);
}
