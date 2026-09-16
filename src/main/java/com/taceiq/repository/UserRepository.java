package com.taceiq.repository;

import com.taceiq.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameAndOrganisationOrgId(String username, Long orgId);

    Optional<User> findByIdAndOrganisationOrgId(Long id, Long orgId);

    // Via organisation relationship: User.organisation.orgId
    List<User> findByOrganisationOrgId(Long orgId);

    List<User> findByStatus(String status);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndOrganisationOrgId(String username, Long orgId);

    List<User> findByRoleId(Long roleId);

    List<User> findByRoleIdAndOrganisationOrgId(Long roleId, Long orgId);
}
