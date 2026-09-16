package com.taceiq.service;

import com.taceiq.dto.*;
import com.taceiq.entity.Complaint;
import com.taceiq.entity.Integration;
import com.taceiq.entity.Investigation;
import com.taceiq.entity.Organisation;
import com.taceiq.graph.service.GraphReadinessService;
import com.taceiq.repository.ComplaintRepository;
import com.taceiq.repository.IntegrationRepository;
import com.taceiq.repository.InvestigationRepository;
import com.taceiq.repository.OrganisationRepository;
import com.taceiq.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final InvestigationRepository investigationRepository;
    private final IntegrationRepository integrationRepository;
    private final OrganisationRepository organisationRepository;
    private final AuthorizationService authorizationService;
    private final GraphReadinessService graphReadinessService;
    private com.taceiq.ingestion.EvidenceDiscoveryService discoveryService;

    private void ensureGraphReady(Long orgId) {
        if (!graphReadinessService.isOrgGraphReady(orgId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Graph not ready for organisation " + orgId);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ComplaintService(ComplaintRepository complaintRepository,
                            InvestigationRepository investigationRepository,
                            IntegrationRepository integrationRepository,
                            OrganisationRepository organisationRepository,
                            AuthorizationService authorizationService,
                            GraphReadinessService graphReadinessService,
                            @org.springframework.beans.factory.annotation.Autowired(required = false) com.taceiq.ingestion.EvidenceDiscoveryService discoveryService) {
        this.complaintRepository = complaintRepository;
        this.investigationRepository = investigationRepository;
        this.integrationRepository = integrationRepository;
        this.organisationRepository = organisationRepository;
        this.authorizationService = authorizationService;
        this.graphReadinessService = graphReadinessService;
        this.discoveryService = discoveryService;
    }

    public ComplaintService(ComplaintRepository complaintRepository,
                            InvestigationRepository investigationRepository,
                            IntegrationRepository integrationRepository,
                            OrganisationRepository organisationRepository,
                            AuthorizationService authorizationService,
                            GraphReadinessService graphReadinessService) {
        this(complaintRepository, investigationRepository, integrationRepository, organisationRepository, authorizationService, graphReadinessService, null);
    }

    public void setDiscoveryService(com.taceiq.ingestion.EvidenceDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    private void registerAsyncDiscovery(Long incidentId, Long orgId) {
        if (discoveryService == null || incidentId == null || orgId == null) return;
        Runnable task = () -> {
            try {
                discoveryService.discoverForIncident(incidentId, orgId);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(ComplaintService.class)
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

    @Transactional
    public ComplaintResponse createManual(CreateComplaintRequest req) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();

        String key = trim(req.getComplaintKey());
        String title = trim(req.getTitle());
        String desc = trim(req.getDescription());
        String batchRef = trim(req.getBatchReference());
        String extRef = trim(req.getExternalReference());
        Instant raisedAt = parseInstant(req.getRaisedAt());

        validate(key, title, desc, batchRef, extRef);

        if (complaintRepository.existsByComplaintKeyAndOrganisationOrgId(key, orgId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Complaint already exists with key: " + key + " for orgId: " + orgId);
        }

        Organisation orgRef = organisationRepository.getReferenceById(orgId);

        // Create/link Investigation using existing workflow if not already linked
        String invKey = key.toUpperCase().startsWith("INV-") ? key : "INV-" + key;
        Investigation inv = null;
        var existingInvOpt = investigationRepository.findByInvestigationKeyAndOrganisationOrgId(invKey, orgId);
        if (!existingInvOpt.isPresent() && !invKey.equals(key)) {
            existingInvOpt = investigationRepository.findByInvestigationKeyAndOrganisationOrgId(key, orgId);
        }
        if (existingInvOpt.isPresent()) {
            inv = existingInvOpt.get();
        } else {
            inv = Investigation.builder()
                    .organisation(orgRef)
                    .investigationKey(invKey)
                    .title(title)
                    .description(desc)
                    .batchReference(batchRef)
                    .status("DRAFT")
                    .createdBy(authorizationService.getCurrentUser())
                    .build();
            try {
                inv = investigationRepository.save(inv);
            } catch (Exception e) {
                inv = investigationRepository.findByInvestigationKeyAndOrganisationOrgId(invKey, orgId)
                        .orElse(null);
            }
        }

        Complaint c = Complaint.builder()
                .organisation(orgRef)
                .complaintKey(key)
                .investigation(inv)
                .title(title)
                .description(desc)
                .batchReference(batchRef)
                .externalReference(extRef)
                .sourceType("MANUAL")
                .raisedAt(raisedAt)
                .createdBy(authorizationService.getCurrentUser())
                .build();
        Complaint saved = complaintRepository.save(c);

        if (inv != null && inv.getBatchReference() != null && !inv.getBatchReference().isBlank()) {
            registerAsyncDiscovery(inv.getId(), orgId);
        }

        return ComplaintResponse.fromEntity(saved);
    }

    /**
     * Domain boundary for future integration ingestion – idempotent via org+integration+externalReference
     */
    @Transactional
    public ComplaintResponse createFromIntegration(Long orgId, Long integrationId, String externalReference, String title, String description, String complaintKey) {
        // orgId must come from integration's owning organisation, not client
        Integration integration = integrationRepository.findByIdAndOrganisationOrgId(integrationId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Integration not found with id: " + integrationId));
        String extRef = trim(externalReference);
        if (extRef == null || extRef.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "externalReference is required for integration intake");
        // Idempotency: if exists return existing
        var existing = complaintRepository.findByOrganisationOrgIdAndIntegrationIdAndExternalReference(orgId, integrationId, extRef);
        if (existing.isPresent()) {
            return ComplaintResponse.fromEntity(existing.get());
        }
        // Manual complaintKey handling for integration: if not provided, derive from externalReference
        String key = trim(complaintKey);
        if (key == null || key.isEmpty()) key = "INT-" + extRef;
        if (complaintRepository.existsByComplaintKeyAndOrganisationOrgId(key, orgId)) {
            // If derived key collides, append suffix
            key = key + "-" + System.currentTimeMillis() % 10000;
        }
        String t = trim(title);
        if (t == null || t.isEmpty()) t = "Integration complaint " + extRef;
        Complaint c = Complaint.builder()
                .organisation(organisationRepository.getReferenceById(orgId))
                .integration(integration)
                .complaintKey(key)
                .title(t)
                .description(trim(description))
                .externalReference(extRef)
                .sourceType("INTEGRATION")
                .createdBy(null) // system
                .build();
        Complaint saved = complaintRepository.save(c);
        return ComplaintResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public ComplaintResponse getComplaint(Long complaintId) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        Complaint c = complaintRepository.findByIdAndOrganisationOrgId(complaintId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found with id: " + complaintId));
        return ComplaintResponse.fromEntity(c);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ComplaintResponse> listComplaints(org.springframework.data.domain.Pageable pageable) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        org.springframework.data.domain.Pageable sorted = pageable;
        if (pageable.getSort().isUnsorted()) {
            sorted = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    org.springframework.data.domain.Sort.by("createdAt").descending().and(org.springframework.data.domain.Sort.by("id").descending()));
        }
        if (sorted.getPageSize() > 100) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be 1-100");
        }
        return complaintRepository.findByOrganisationOrgId(orgId, sorted).map(ComplaintResponse::fromEntity);
    }

    @Transactional
    public InvestigationResponse createInvestigationFromComplaint(Long complaintId, CreateInvestigationFromComplaintRequest req) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();

        Complaint complaint = complaintRepository.findByIdAndOrganisationOrgId(complaintId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found with id: " + complaintId));

        if (complaint.getInvestigation() != null) {
            return InvestigationResponse.fromEntity(complaint.getInvestigation());
        }

        String invKey = trim(req.getInvestigationKey());
        String title = trim(req.getTitle());
        String desc = trim(req.getDescription());
        if (invKey == null || invKey.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "investigationKey is required");
        if (title == null || title.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
        if (invKey.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "investigationKey max 100");
        if (title.length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title max 200");
        if (desc != null && desc.length() > 2000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "description max 2000");

        if (investigationRepository.existsByInvestigationKeyAndOrganisationOrgId(invKey, orgId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Investigation already exists with key: " + invKey + " for orgId: " + orgId);
        }

        var existingInvOpt = investigationRepository.findByInvestigationKeyAndOrganisationOrgId(invKey, orgId);
        Investigation savedInv;
        if (existingInvOpt.isPresent()) {
            savedInv = existingInvOpt.get();
        } else {
            savedInv = com.taceiq.entity.Investigation.builder()
                    .organisation(organisationRepository.getReferenceById(orgId))
                    .investigationKey(invKey)
                    .title(title)
                    .description(desc)
                    .batchReference(complaint.getBatchReference())
                    .productReference(null)
                    .orderReference(null)
                    .status("DRAFT")
                    .createdBy(authorizationService.getCurrentUser())
                    .build();
            savedInv = investigationRepository.save(savedInv);
        }

        complaint.setInvestigation(savedInv);
        complaintRepository.save(complaint);

        // Async discovery trigger
        if (savedInv.getBatchReference() != null && !savedInv.getBatchReference().isBlank()) {
            registerAsyncDiscovery(savedInv.getId(), orgId);
        }
        return InvestigationResponse.fromEntity(savedInv);
    }

    private String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private void validate(String key, String title, String desc, String batchRef, String extRef) {
        if (key == null || key.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "complaintKey is required");
        if (title == null || title.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
        if (key.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "complaintKey max 100");
        if (title.length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title max 200");
        if (desc != null && desc.length() > 2000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "description max 2000");
        if (batchRef != null && batchRef.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "batchReference max 100");
        if (extRef != null && extRef.length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "externalReference max 200");
    }

    private Instant parseInstant(String iso) {
        if (iso == null || iso.trim().isEmpty()) return null;
        try {
            return Instant.parse(iso.trim());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid raisedAt: " + iso);
        }
    }
}
