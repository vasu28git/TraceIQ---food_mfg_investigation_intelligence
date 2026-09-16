package com.taceiq.repository;

import com.taceiq.entity.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByNameAndOrganisationOrgId(String name, Long orgId);

    Optional<Role> findByIdAndOrganisationOrgId(Long id, Long orgId);

    @EntityGraph(attributePaths = {"permissions"})
    Optional<Role> findWithPermissionsByIdAndOrganisationOrgId(Long id, Long orgId);

    List<Role> findByOrganisationOrgId(Long orgId);

    boolean existsByNameAndOrganisationOrgId(String name, Long orgId);
}
