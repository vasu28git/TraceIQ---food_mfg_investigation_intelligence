package com.taceiq.repository;

import com.taceiq.entity.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganisationRepository extends JpaRepository<Organisation, Long> {

    Optional<Organisation> findByName(String name);

    Optional<Organisation> findByDomain(String domain);

    List<Organisation> findByStatus(String status);

    boolean existsByDomain(String domain);

    boolean existsByName(String name);
}
