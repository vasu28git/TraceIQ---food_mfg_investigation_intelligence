package com.investigation.platform.role.repository;

import com.investigation.platform.role.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Page<Role> findByOrgId(UUID orgId, Pageable pageable);

    Optional<Role> findByRoleIdAndOrgId(UUID roleId, UUID orgId);

    Optional<Role> findByNameAndOrgId(String name, UUID orgId);

    boolean existsByNameAndOrgId(String name, UUID orgId);

    boolean existsByRoleIdAndOrgId(UUID roleId, UUID orgId);

    void deleteByRoleIdAndOrgId(UUID roleId, UUID orgId);

    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.roleId = :roleId AND r.orgId = :orgId")
    Optional<Role> findByIdWithPermissions(@Param("roleId") UUID roleId, @Param("orgId") UUID orgId);
}
