package com.taceiq.service;

import com.taceiq.dto.CreateInvestigationRequest;
import com.taceiq.dto.InvestigationResponse;
import com.taceiq.entity.Investigation;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.ComplaintRepository;
import com.taceiq.repository.InvestigationEvidenceRepository;
import com.taceiq.repository.InvestigationRepository;
import com.taceiq.repository.OrganisationRepository;
import com.taceiq.security.AuthorizationService;
import com.taceiq.graph.service.GraphReadinessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InvestigationService {

    private final InvestigationRepository investigationRepository;
    private final OrganisationRepository organisationRepository;
    private final AuthorizationService authorizationService;
    private final GraphReadinessService graphReadinessService;
    private final CanonicalEvidenceRepository canonicalEvidenceRepository;
    private final InvestigationEvidenceRepository evidenceLinkRepository;
    private final ComplaintRepository complaintRepository;

    @Autowired(required = false)
    private com.taceiq.ingestion.EvidenceDiscoveryService discoveryService;

    @Autowired
    public InvestigationService(InvestigationRepository investigationRepository,
                              OrganisationRepository organisationRepository,
                              AuthorizationService authorizationService,
                              GraphReadinessService graphReadinessService,
                              CanonicalEvidenceRepository canonicalEvidenceRepository,
                              InvestigationEvidenceRepository evidenceLinkRepository,
                              ComplaintRepository complaintRepository) {
        this.investigationRepository = investigationRepository;
        this.organisationRepository = organisationRepository;
        this.authorizationService = authorizationService;
        this.graphReadinessService = graphReadinessService;
        this.canonicalEvidenceRepository = canonicalEvidenceRepository;
        this.evidenceLinkRepository = evidenceLinkRepository;
        this.complaintRepository = complaintRepository;
    }

    // Backward compat for tests with 4 args
    public InvestigationService(InvestigationRepository investigationRepository,
                              OrganisationRepository organisationRepository,
                              AuthorizationService authorizationService,
                              GraphReadinessService graphReadinessService) {
                    this(investigationRepository, organisationRepository, authorizationService, graphReadinessService, null, null, null);
    }


    private void ensureGraphReady(Long orgId) {
        boolean ready = graphReadinessService.isOrgGraphReady(orgId);
        // Diagnostic logging for manual upload graph readiness
        try {
            var all = graphReadinessService.getCanonicalEvidenceCountsForDiagnostics(orgId);
            var proj = graphReadinessService.getGraphValidationForDiagnostics(orgId);
            org.slf4j.LoggerFactory.getLogger(InvestigationService.class).info("Graph readiness diagnostic orgId={} ready={} canonicalTotal={} manualCount={} integrationCount={} graphEvidenceCount={} graphValid={}",
                    orgId, ready, all.get("total"), all.get("manual"), all.get("integration"), proj.getEvidenceCount(), proj.isValid());
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(InvestigationService.class).warn("Graph readiness diagnostic failed for org {}: {}", orgId, e.getMessage());
        }
        if (!ready) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Graph not ready for organisation " + orgId);
        }
    }

    @Transactional
    public InvestigationResponse create(CreateInvestigationRequest req) {
        // Phase 2: Incident creation MUST NOT require Neo4j — PostgreSQL is source of truth.
        // Previous ensureGraphReady(orgId) removed intentionally; only auth + validation remain.
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();

        String key = req.getInvestigationKey() != null ? req.getInvestigationKey().trim() : null;
        String title = req.getTitle() != null ? req.getTitle().trim() : null;
        String desc = req.getDescription() != null ? req.getDescription().trim() : null;
        if (desc != null && desc.isEmpty()) desc = null;

        if (key == null || key.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "investigationKey is required");
        if (title == null || title.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
        if (key.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "investigationKey max 100");
        if (title.length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title max 200");
        if (desc != null && desc.length() > 2000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "description max 2000");

        // Incident context — optional, Incident domain alias for Investigation persistence
        String batchRef = req.getBatchReference() != null ? req.getBatchReference().trim() : null;
        if (batchRef != null && batchRef.isEmpty()) batchRef = null;
        String productRef = req.getProductReference() != null ? req.getProductReference().trim() : null;
        if (productRef != null && productRef.isEmpty()) productRef = null;
        String orderRef = req.getOrderReference() != null ? req.getOrderReference().trim() : null;
        if (orderRef != null && orderRef.isEmpty()) orderRef = null;
        if (batchRef != null && batchRef.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "batchReference max 100");
        if (productRef != null && productRef.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productReference max 100");
        if (orderRef != null && orderRef.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderReference max 100");

        java.time.Instant start = req.getIncidentStart();
        java.time.Instant end = req.getIncidentEnd();
        if (start != null && end != null && end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "incidentEnd must not be before incidentStart");
        }

        if (investigationRepository.existsByInvestigationKeyAndOrganisationOrgId(key, orgId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Investigation already exists with key: " + key + " for orgId: " + orgId);
        }

        Investigation inv = Investigation.builder()
                .organisation(organisationRepository.getReferenceById(orgId))
                .investigationKey(key)
                .title(title)
                .description(desc)
                .batchReference(batchRef)
                .productReference(productRef)
                .orderReference(orderRef)
                .incidentStart(start)
                .incidentEnd(end)
                .status("DRAFT")
                .createdBy(authorizationService.getCurrentUser())
                .build();
        Investigation saved = investigationRepository.save(inv);
        // Automatic batch discovery best-effort (must not fail incident creation)
        if (batchRef != null && !batchRef.isBlank()) {
            registerAsyncDiscovery(saved.getId(), orgId);
        }
        return InvestigationResponse.fromEntity(saved);
    }

    private void registerAsyncDiscovery(Long incidentId, Long orgId) {
        if (discoveryService == null || incidentId == null || orgId == null) return;
        Runnable task = () -> {
            try {
                discoveryService.discoverForIncident(incidentId, orgId);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(InvestigationService.class)
                        .warn("Auto-discovery failed for incident {} org {}: {}", incidentId, orgId, e.getMessage());
            }
        };

        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            java.util.concurrent.CompletableFuture.runAsync(task);
                        }
                    }
            );
        } else {
            java.util.concurrent.CompletableFuture.runAsync(task);
        }
    }

    private Investigation loadTenant(Long id) {
        Long orgId = authorizationService.getCurrentOrgId();
        return investigationRepository.findByIdAndOrganisationOrgId(id, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Investigation not found with id: " + id));
    }

    public InvestigationResponse getInvestigation(Long id) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        Investigation inv = investigationRepository.findByIdAndOrganisationOrgId(id, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Investigation not found with id: " + id));
        return InvestigationResponse.fromEntity(inv);
    }

    @Transactional
    public com.taceiq.ingestion.EvidenceDiscoveryService.DiscoveryResult discoverEvidence(Long id) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        if (discoveryService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Evidence discovery is unavailable");
        }
        loadTenant(id);
        return discoveryService.discoverForIncident(id, orgId);
    }

    public org.springframework.data.domain.Page<InvestigationResponse> listInvestigations(org.springframework.data.domain.Pageable pageable) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        org.springframework.data.domain.Pageable sorted = pageable;
        // Default deterministic ordering if no sort provided
        if (pageable.getSort().isUnsorted()) {
            sorted = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    org.springframework.data.domain.Sort.by("createdAt").descending().and(org.springframework.data.domain.Sort.by("id").descending()));
        }
        // Enforce max size 100 per existing project convention
        if (sorted.getPageSize() > 100) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be 1-100");
        }
        return investigationRepository.findByOrganisationOrgId(orgId, sorted).map(investigation -> {
            InvestigationResponse response = InvestigationResponse.fromEntity(investigation);
            if (complaintRepository == null) return response;
            return response.toBuilder().complaintKey(complaintRepository.findByInvestigationIdAndOrganisationOrgId(investigation.getId(), orgId)
                .map(complaint -> complaint.getComplaintKey()).orElse(null)).build();
        });
    }

    private InvestigationResponse transition(Long id, String from, String to) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        ensureGraphReady(orgId);
        Investigation inv = loadTenant(id);
        if (!from.equals(inv.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid transition: expected " + from + " but was " + inv.getStatus());
        }
        inv.setStatus(to);
        Investigation saved = investigationRepository.save(inv);
        return InvestigationResponse.fromEntity(saved);
    }

    @Transactional
    public InvestigationResponse activate(Long id) {
        return transition(id, "DRAFT", "ACTIVE");
    }

    @Transactional
    public InvestigationResponse complete(Long id) {
        return transition(id, "ACTIVE", "COMPLETED");
    }

    @Transactional
    public InvestigationResponse archive(Long id) {
        return transition(id, "COMPLETED", "ARCHIVED");
    }
}

