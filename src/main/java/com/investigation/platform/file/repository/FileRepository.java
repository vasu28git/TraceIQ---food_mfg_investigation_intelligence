package com.investigation.platform.file.repository;

import com.investigation.platform.file.entity.File;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileRepository extends JpaRepository<File, UUID> {

    Page<File> findByOrgId(UUID orgId, Pageable pageable);

    Optional<File> findByFileIdAndOrgId(UUID fileId, UUID orgId);

    Page<File> findByOrgIdAndIntegrationId(UUID orgId, UUID integrationId, Pageable pageable);

    boolean existsByFileIdAndOrgId(UUID fileId, UUID orgId);

    void deleteByFileIdAndOrgId(UUID fileId, UUID orgId);
}
