package com.investigation.platform.permission.repository;

import com.investigation.platform.permission.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByName(String name);

    Set<Permission> findByNameIn(Set<String> names);

    boolean existsByName(String name);
}
