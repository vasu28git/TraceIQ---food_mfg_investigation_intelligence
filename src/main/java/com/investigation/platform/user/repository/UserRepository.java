package com.investigation.platform.user.repository;

import com.investigation.platform.user.entity.User;
import com.investigation.platform.user.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Page<User> findByOrgId(UUID orgId, Pageable pageable);

    Optional<User> findByUserIdAndOrgId(UUID userId, UUID orgId);

    Optional<User> findByEmailAndOrgId(String email, UUID orgId);

    Optional<User> findByEmail(String email);

    boolean existsByEmailAndOrgId(String email, UUID orgId);

    boolean existsByUserIdAndOrgId(UUID userId, UUID orgId);

    Page<User> findByOrgIdAndStatus(UUID orgId, UserStatus status, Pageable pageable);

    void deleteByUserIdAndOrgId(UUID userId, UUID orgId);

    @Query("SELECT u FROM User u JOIN FETCH u.role r LEFT JOIN FETCH r.permissions WHERE u.email = :email")
    Optional<User> findByEmailWithRoleAndPermissions(@Param("email") String email);
}
