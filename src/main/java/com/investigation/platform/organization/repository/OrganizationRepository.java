package com.investigation.platform.organization.repository;

import com.investigation.platform.organization.entity.Organization;
import com.investigation.platform.organization.enums.OrganizationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findByDomain(String domain);

    boolean existsByDomain(String domain);

    boolean existsByName(String name);

    Page<Organization> findByStatus(OrganizationStatus status, Pageable pageable);
}
