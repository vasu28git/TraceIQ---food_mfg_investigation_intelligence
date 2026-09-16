package com.taceiq.repository;

import com.taceiq.entity.CanonicalEvidence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CanonicalEvidenceRepository extends JpaRepository<CanonicalEvidence, Long> {

    Optional<CanonicalEvidence> findByIdAndOrganisationOrgId(Long id, Long orgId);

    List<CanonicalEvidence> findAllByOrganisationOrgId(Long orgId);

    List<CanonicalEvidence> findByIntegrationIdAndOrganisationOrgId(Long integrationId, Long orgId);

    Optional<CanonicalEvidence> findByExternalIdAndIntegrationIdAndOrganisationOrgId(String externalId, Long integrationId, Long orgId);

    List<CanonicalEvidence> findByCaseIdAndOrganisationOrgId(String caseId, Long orgId);

    List<CanonicalEvidence> findByCaseIdAndIntegrationIdAndOrganisationOrgId(String caseId, Long integrationId, Long orgId);

    List<CanonicalEvidence> findByActorIdAndOrganisationOrgId(String actorId, Long orgId);

    List<CanonicalEvidence> findByActorIdAndIntegrationIdAndOrganisationOrgId(String actorId, Long integrationId, Long orgId);

    List<CanonicalEvidence> findByParentIdAndOrganisationOrgId(String parentId, Long orgId);

    List<CanonicalEvidence> findBySourceUpdatedAtGreaterThanEqualAndOrganisationOrgId(java.time.Instant since, Long orgId);

    List<CanonicalEvidence> findByIntegrationIdAndOrganisationOrgIdOrderBySourceUpdatedAtDesc(Long integrationId, Long orgId);

    boolean existsByExternalIdAndIntegrationIdAndOrganisationOrgId(String externalId, Long integrationId, Long orgId);

    java.util.Optional<CanonicalEvidence> findByExternalIdAndOrganisationOrgId(String externalId, Long orgId);

    List<CanonicalEvidence> findByExternalIdInAndOrganisationOrgId(java.util.Collection<String> externalIds, Long orgId);

    Page<CanonicalEvidence> findByCaseIdAndOrganisationOrgId(String caseId, Long orgId, Pageable pageable);

    long countByCaseIdAndOrganisationOrgId(String caseId, Long orgId);

    @Query("SELECT c FROM CanonicalEvidence c WHERE c.organisation.orgId = :orgId AND c.isDeleted = false AND (:caseId IS NULL OR c.caseId = :caseId) AND (:actorId IS NULL OR c.actorId = :actorId)")
    Page<CanonicalEvidence> findByOrgAndCaseAndActor(@Param("orgId") Long orgId, @Param("caseId") String caseId, @Param("actorId") String actorId, Pageable pageable);

    @Query("SELECT COUNT(c) FROM CanonicalEvidence c WHERE c.organisation.orgId = :orgId AND c.isDeleted = false AND (:caseId IS NULL OR c.caseId = :caseId) AND (:actorId IS NULL OR c.actorId = :actorId)")
    long countByOrgAndCaseAndActor(@Param("orgId") Long orgId, @Param("caseId") String caseId, @Param("actorId") String actorId);

    Page<CanonicalEvidence> findByOrganisationOrgIdAndIsDeletedFalse(Long orgId, Pageable pageable);

    long countByOrganisationOrgIdAndIsDeletedFalse(Long orgId);

        @Query("SELECT c FROM CanonicalEvidence c WHERE c.organisation.orgId = :orgId AND c.isDeleted = false "
            + "AND (:caseId IS NULL OR c.caseId = :caseId) "
            + "AND (:actorId IS NULL OR c.actorId = :actorId) "
            + "AND NOT EXISTS (SELECT ie.id FROM InvestigationEvidence ie WHERE ie.organisation.orgId = :orgId "
            + "AND ie.investigation.id = :investigationId AND ie.canonicalEvidence.id = c.id)")
        Page<CanonicalEvidence> findAvailableForOrganisationAndInvestigation(@Param("orgId") Long orgId,
                                           @Param("caseId") String caseId,
                                           @Param("actorId") String actorId,
                                           @Param("investigationId") Long investigationId,
                                           Pageable pageable);

        @Query("SELECT COUNT(c) FROM CanonicalEvidence c WHERE c.organisation.orgId = :orgId AND c.isDeleted = false "
            + "AND (:caseId IS NULL OR c.caseId = :caseId) "
            + "AND (:actorId IS NULL OR c.actorId = :actorId) "
            + "AND NOT EXISTS (SELECT ie.id FROM InvestigationEvidence ie WHERE ie.organisation.orgId = :orgId "
            + "AND ie.investigation.id = :investigationId AND ie.canonicalEvidence.id = c.id)")
        long countAvailableForOrganisationAndInvestigation(@Param("orgId") Long orgId,
                                @Param("caseId") String caseId,
                                @Param("actorId") String actorId,
                                @Param("investigationId") Long investigationId);

    // Incident-scoped (Phase 1) — incident is Investigation FK
    List<CanonicalEvidence> findByIncidentIdAndOrganisationOrgId(Long incidentId, Long orgId);

    long countByIncidentIdAndOrganisationOrgId(Long incidentId, Long orgId);
}
