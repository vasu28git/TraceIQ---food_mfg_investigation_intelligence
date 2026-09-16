package com.taceiq.repository;

import com.taceiq.entity.IngestedSourceRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IngestedSourceRecordRepository extends JpaRepository<IngestedSourceRecord, Long> {

    List<IngestedSourceRecord> findByOrganisationOrgId(Long orgId);

    Page<IngestedSourceRecord> findByOrganisationOrgId(Long orgId, Pageable pageable);

    List<IngestedSourceRecord> findByOrganisationOrgIdAndBatchReference(Long orgId, String batchReference);

    List<IngestedSourceRecord> findByOrganisationOrgIdAndMachineReference(Long orgId, String machineReference);

    List<IngestedSourceRecord> findByOrganisationOrgIdAndSupplierReference(Long orgId, String supplierReference);

    List<IngestedSourceRecord> findByOrganisationOrgIdAndProductReference(Long orgId, String productReference);

    List<IngestedSourceRecord> findByOrganisationOrgIdAndSourceType(Long orgId, String sourceType);

    Optional<IngestedSourceRecord> findByOrganisationOrgIdAndSourceTypeAndSourceRecordId(Long orgId, String sourceType, String sourceRecordId);

    Optional<IngestedSourceRecord> findFirstByOrganisationOrgIdAndSourceRecordId(Long orgId, String sourceRecordId);

    long countByOrganisationOrgId(Long orgId);

    long countByOrganisationOrgIdAndBatchReference(Long orgId, String batchReference);

    long countByOrganisationOrgIdAndSourceType(Long orgId, String sourceType);

    boolean existsByOrganisationOrgIdAndSourceTypeAndSourceRecordId(Long orgId, String sourceType, String sourceRecordId);
}
