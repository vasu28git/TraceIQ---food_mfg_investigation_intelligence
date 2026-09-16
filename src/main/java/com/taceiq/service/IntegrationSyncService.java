package com.taceiq.service;

import com.taceiq.dto.SyncResponse;
import com.taceiq.entity.Integration;
import com.taceiq.entity.IntegrationSync;
import com.taceiq.integration.EvidenceApiClient;
import com.taceiq.integration.EvidenceApiConfig;
import com.taceiq.integration.dto.ExternalEvidenceRecord;
import com.taceiq.repository.IntegrationRepository;
import com.taceiq.repository.IntegrationSyncRepository;
import com.taceiq.repository.OrganisationRepository;
import com.taceiq.security.AuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.Investigation;
import com.taceiq.integration.EvidenceNormalizer;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.InvestigationRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class IntegrationSyncService {

    private final IntegrationRepository integrationRepository;
    private final IntegrationSyncRepository syncRepository;
    private final OrganisationRepository organisationRepository;
    private final AuthorizationService authorizationService;
    private final EvidenceApiClient evidenceApiClient;
    private final CanonicalEvidenceRepository canonicalEvidenceRepository;
    private final EvidenceNormalizer evidenceNormalizer;
    private final com.taceiq.graph.service.GraphProjectionService graphProjectionService;
    private final com.taceiq.graph.service.GraphValidator graphValidator;
    private final InvestigationRepository investigationRepository;

    @Autowired
    public IntegrationSyncService(IntegrationRepository integrationRepository,
                                  IntegrationSyncRepository syncRepository,
                                  OrganisationRepository organisationRepository,
                                  AuthorizationService authorizationService,
                                  EvidenceApiClient evidenceApiClient,
                                  CanonicalEvidenceRepository canonicalEvidenceRepository,
                                  EvidenceNormalizer evidenceNormalizer,
                                  com.taceiq.graph.service.GraphProjectionService graphProjectionService,
                                  com.taceiq.graph.service.GraphValidator graphValidator,
                                  InvestigationRepository investigationRepository) {
        this.integrationRepository = integrationRepository;
        this.syncRepository = syncRepository;
        this.organisationRepository = organisationRepository;
        this.authorizationService = authorizationService;
        this.evidenceApiClient = evidenceApiClient;
        this.canonicalEvidenceRepository = canonicalEvidenceRepository;
        this.evidenceNormalizer = evidenceNormalizer;
        this.graphProjectionService = graphProjectionService;
        this.graphValidator = graphValidator;
        this.investigationRepository = investigationRepository;
    }

    // Backward compat for tests that construct with 4 args (no client)
    public IntegrationSyncService(IntegrationRepository integrationRepository,
                                   IntegrationSyncRepository syncRepository,
                                   OrganisationRepository organisationRepository,
                                   AuthorizationService authorizationService) {
        this(integrationRepository, syncRepository, organisationRepository, authorizationService, null, null, null, null, null, null);
    }

    public IntegrationSyncService(IntegrationRepository integrationRepository,
                                   IntegrationSyncRepository syncRepository,
                                   OrganisationRepository organisationRepository,
                                   AuthorizationService authorizationService,
                                   EvidenceApiClient evidenceApiClient) {
        this(integrationRepository, syncRepository, organisationRepository, authorizationService, evidenceApiClient, null, null, null, null, null);
    }

    // Backward compat for tests that construct with 9 args (without InvestigationRepository)
    public IntegrationSyncService(IntegrationRepository integrationRepository,
                                   IntegrationSyncRepository syncRepository,
                                   OrganisationRepository organisationRepository,
                                   AuthorizationService authorizationService,
                                   EvidenceApiClient evidenceApiClient,
                                   CanonicalEvidenceRepository canonicalEvidenceRepository,
                                   EvidenceNormalizer evidenceNormalizer,
                                   com.taceiq.graph.service.GraphProjectionService graphProjectionService,
                                   com.taceiq.graph.service.GraphValidator graphValidator) {
        this(integrationRepository, syncRepository, organisationRepository, authorizationService, evidenceApiClient, canonicalEvidenceRepository, evidenceNormalizer, graphProjectionService, graphValidator, null);
    }

    /**
     * Creates a PENDING sync for the given integration, tenant-scoped.
     * Permission: uses existing integration read permission (closest existing).
     * Decision: No dedicated sync permission exists among 71 permissions; INTEGRATION_READ covers access/action on integration.
     */
    @Transactional
    public SyncResponse createPending(Long integrationId) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireIntegrationRead();

        Integration integration = integrationRepository.findByIdAndOrganisationOrgId(integrationId, orgId)
                .orElseGet(() -> {
                    if (integrationRepository.findById(integrationId).isPresent()) {
                        throw new AccessDeniedException("Integration does not belong to your organisation");
                    }
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Integration not found with id: " + integrationId);
                });

        IntegrationSync sync = IntegrationSync.builder()
                .organisation(organisationRepository.getReferenceById(orgId))
                .integration(integration)
                .status("PENDING")
                .retryCount(0)
                .recordsFetched(0)
                .recordsCreated(0)
                .recordsUpdated(0)
                .recordsSkipped(0)
                .recordsFailed(0)
                .build();

        IntegrationSync saved = syncRepository.save(sync);
        return SyncResponse.builder()
                .syncId(saved.getId())
                .status(saved.getStatus())
                .build();
    }

    @Transactional
    public SyncResponse syncIntegration(Long integrationId) {
        return syncIntegration(integrationId, null);
    }

    @Transactional
    public SyncResponse syncIntegration(Long integrationId, Long incidentId) {
        // Validate incident belongs to current org when provided — before creating pending so no orphan sync on invalid incident
        Long orgIdForValidation = null;
        Investigation incident = null;
        if (incidentId != null) {
            orgIdForValidation = authorizationService.getCurrentOrgId();
            if (investigationRepository == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Incident validation not configured");
            }
            final Long orgId = orgIdForValidation;
            incident = investigationRepository.findByIdAndOrganisationOrgId(incidentId, orgId)
                    .orElseGet(() -> {
                        if (investigationRepository.findById(incidentId).isPresent()) {
                            throw new AccessDeniedException("Incident does not belong to your organisation");
                        }
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found with id: " + incidentId);
                    });
        }
        // Orchestrate: create pending → fetch → persist → project → validate
        // Reuses existing fetchEvidenceForSync and completeSync for testability
        SyncResponse pending = createPending(integrationId);
        Long syncId = pending.getSyncId();
        Long orgId = authorizationService.getCurrentOrgId();
        try {
            // Fetch with default limit 100 and no updatedSince (full sync)
            var records = fetchEvidenceForSync(syncId, 100, null);
            SyncResponse completed = completeSync(syncId, records, incident != null ? incident.getId() : incidentId);
            // Project to Neo4j (best effort, do not fail sync if projection fails)
            try {
                if (graphProjectionService != null && graphValidator != null) {
                    graphProjectionService.projectForIntegration(orgId, integrationId);
                    // Validation is done via GraphReadinessService on next check, but we warm it here
                    graphValidator.validate(orgId);
                }
            } catch (Exception e) {
                // Log and continue - sync status already SUCCESS/PARTIAL, graph readiness will reflect projection failure
                org.slf4j.LoggerFactory.getLogger(IntegrationSyncService.class).warn("Graph projection after sync failed for integration {}: {}", integrationId, e.getMessage());
            }
            return completed;
        } catch (Exception e) {
            // Mark sync as FAILED if fetch failed
            try {
                var syncOpt = syncRepository.findById(syncId);
                if (syncOpt.isPresent()) {
                    var sync = syncOpt.get();
                    sync.setStatus("FAILED");
                    sync.setErrorSummary(e.getMessage() != null ? e.getMessage().substring(0, Math.min(1000, e.getMessage().length())) : e.getClass().getSimpleName());
                    sync.setCompletedAt(Instant.now());
                    syncRepository.save(sync);
                    return SyncResponse.builder().syncId(sync.getId()).status(sync.getStatus()).build();
                }
            } catch (Exception ignored) {}
            throw e;
        }
    }

    @Transactional
    public SyncResponse markRunning(Long syncId) {
        Long orgId = authorizationService.getCurrentOrgId();
        IntegrationSync sync = syncRepository.findByIdAndOrganisationOrgId(syncId, orgId)
                .orElseGet(() -> {
                    if (syncRepository.findById(syncId).isPresent()) {
                        throw new AccessDeniedException("Sync does not belong to your organisation");
                    }
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sync not found with id: " + syncId);
                });
        sync.setStatus("RUNNING");
        sync.setStartedAt(Instant.now());
        IntegrationSync saved = syncRepository.save(sync);
        return SyncResponse.builder().syncId(saved.getId()).status(saved.getStatus()).build();
    }

    /**
     * Minimal extraction: PENDING -> RUNNING then fetch via EvidenceApiClient.
     * Does NOT write CanonicalEvidence, does NOT mark SUCCESS/FAILED.
     * Separated so client remains testable without JPA.
     */
    @Transactional
    public List<ExternalEvidenceRecord> fetchEvidenceForSync(Long syncId, int limit, Instant updatedSince) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireIntegrationRead();
        IntegrationSync sync = syncRepository.findByIdAndOrganisationOrgId(syncId, orgId)
                .orElseGet(() -> {
                    if (syncRepository.findById(syncId).isPresent()) throw new AccessDeniedException("Sync does not belong to your organisation");
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sync not found with id: " + syncId);
                });
        if (!"PENDING".equals(sync.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sync is not PENDING, current status: " + sync.getStatus());
        }
        // Transition to RUNNING
        sync.setStatus("RUNNING");
        sync.setStartedAt(Instant.now());
        syncRepository.save(sync);

        if (evidenceApiClient == null) {
            throw new IllegalStateException("EvidenceApiClient not configured");
        }
        Integration integration = sync.getIntegration();
        EvidenceApiConfig config = EvidenceApiConfig.fromIntegrationConfiguration(integration.getConfiguration());
        // Delegate to client – client handles pagination internally via fetchAll
        return evidenceApiClient.fetchAll(config, limit, updatedSince);
    }

    /**
     * Normalizes and persists external records idempotently.
     * Transaction: single transaction for now; future large syncs should chunk (e.g., 200 records per flush) to avoid holding huge transaction.
     * See docs/phase1-canonical-model.md §7, §9 for extensibility (deletedAt/isDeleted remain unused).
     */
    @Transactional
    public SyncResponse completeSync(Long syncId, List<ExternalEvidenceRecord> records) {
        return completeSync(syncId, records, null);
    }

    @Transactional
    public SyncResponse completeSync(Long syncId, List<ExternalEvidenceRecord> records, Long incidentId) {
        Long orgId = authorizationService.getCurrentOrgId();
        // Tenant check – same as createPending, no org_id from client
        IntegrationSync sync = syncRepository.findByIdAndOrganisationOrgId(syncId, orgId)
                .orElseGet(() -> {
                    if (syncRepository.findById(syncId).isPresent()) throw new AccessDeniedException("Sync does not belong to your organisation");
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sync not found with id: " + syncId);
                });

        if (!"RUNNING".equals(sync.getStatus()) && !"PENDING".equals(sync.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sync is not RUNNING/PENDING, current status: " + sync.getStatus());
        }
        // Ensure RUNNING
        if ("PENDING".equals(sync.getStatus())) {
            sync.setStatus("RUNNING");
            sync.setStartedAt(Instant.now());
        }

        if (evidenceNormalizer == null || canonicalEvidenceRepository == null) {
            throw new IllegalStateException("Normalizer/repository not configured");
        }
        if (records == null) records = List.of();

        // Validate incident belongs to current org when provided
        Investigation incident = null;
        if (incidentId != null) {
            if (investigationRepository == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Incident validation not configured");
            }
            final Long id = incidentId;
            incident = investigationRepository.findByIdAndOrganisationOrgId(id, orgId)
                    .orElseGet(() -> {
                        if (investigationRepository.findById(id).isPresent()) {
                            throw new AccessDeniedException("Incident does not belong to your organisation");
                        }
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found with id: " + id);
                    });
        }

        int fetched = records.size();
        int created = 0, updated = 0, skipped = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        Instant now = Instant.now();

        for (ExternalEvidenceRecord rec : records) {
            try {
                EvidenceNormalizer.NormalizedEvidence norm = evidenceNormalizer.normalize(rec);
                // Tenant-safe lookup – org and integration from sync, NOT payload
                var existingOpt = canonicalEvidenceRepository
                        .findByExternalIdAndIntegrationIdAndOrganisationOrgId(norm.externalId, sync.getIntegration().getId(), orgId);
                if (existingOpt.isEmpty()) {
                    CanonicalEvidence entity = CanonicalEvidence.builder()
                            .organisation(sync.getOrganisation())
                            .integration(sync.getIntegration())
                            .incident(incident)
                            .sync(sync)
                            .externalId(norm.externalId)
                            .caseId(norm.caseId)
                            .actorId(norm.actorId)
                            .parentId(norm.parentId)
                            .title(norm.title)
                            .sourceType(norm.sourceType)
                            .status(norm.status)
                            .sourceCreatedAt(norm.sourceCreatedAt)
                            .sourceUpdatedAt(norm.sourceUpdatedAt)
                            .contentHash(norm.contentHash)
                            .normalizedPayload(norm.normalizedPayload)
                            .firstSeenAt(now)
                            .lastSeenAt(now)
                            .isDeleted(false)
                            .build();
                    canonicalEvidenceRepository.save(entity);
                    created++;
                } else {
                    CanonicalEvidence existing = existingOpt.get();
                    if (norm.contentHash.equals(existing.getContentHash())) {
                        // No meaningful change – still bump lastSeenAt and sync link
                        existing.setLastSeenAt(now);
                        existing.setSync(sync);
                        if (incident != null) existing.setIncident(incident);
                        canonicalEvidenceRepository.save(existing);
                        skipped++;
                    } else {
                        existing.setCaseId(norm.caseId);
                        existing.setActorId(norm.actorId);
                        existing.setParentId(norm.parentId);
                        existing.setTitle(norm.title);
                        existing.setSourceType(norm.sourceType);
                        existing.setStatus(norm.status);
                        existing.setSourceCreatedAt(norm.sourceCreatedAt);
                        existing.setSourceUpdatedAt(norm.sourceUpdatedAt);
                        existing.setContentHash(norm.contentHash);
                        existing.setNormalizedPayload(norm.normalizedPayload);
                        existing.setLastSeenAt(now);
                        existing.setSync(sync);
                        if (incident != null) existing.setIncident(incident);
                        // firstSeenAt preserved
                        canonicalEvidenceRepository.save(existing);
                        updated++;
                    }
                }
            } catch (Exception e) {
                failed++;
                String safe = e.getMessage() != null ? e.getMessage().replaceAll("Bearer\\s+\\S+", "Bearer ***") : e.getClass().getSimpleName();
                // Truncate to avoid huge summary
                if (safe.length() > 200) safe = safe.substring(0, 200);
                errors.add(safe);
            }
        }

        sync.setRecordsFetched(fetched);
        sync.setRecordsCreated(sync.getRecordsCreated() + created);
        sync.setRecordsUpdated(sync.getRecordsUpdated() + updated);
        sync.setRecordsSkipped(sync.getRecordsSkipped() + skipped);
        sync.setRecordsFailed(sync.getRecordsFailed() + failed);
        sync.setCompletedAt(Instant.now());
        if (failed == 0) {
            sync.setStatus("SUCCESS");
        } else if (created + updated + skipped > 0) {
            sync.setStatus("PARTIAL");
        } else {
            sync.setStatus("FAILED");
        }
        if (!errors.isEmpty()) {
            String summary = String.join("; ", errors);
            if (summary.length() > 1000) summary = summary.substring(0, 1000);
            sync.setErrorSummary(summary);
        }
        IntegrationSync saved = syncRepository.save(sync);
        return SyncResponse.builder().syncId(saved.getId()).status(saved.getStatus()).build();
    }
}
