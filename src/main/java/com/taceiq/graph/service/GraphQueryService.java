package com.taceiq.graph.service;

import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.IngestedSourceRecord;
import com.taceiq.graph.GraphQueryRepository;
import com.taceiq.graph.dto.GraphEvidencePageResponse;
import com.taceiq.graph.dto.GraphEvidenceResponse;
import com.taceiq.graph.dto.TraceabilityDtos.CaseTraceabilityResponse;
import com.taceiq.graph.dto.TraceabilityDtos.IncidentTraceabilityResponse;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.ConfigurationRepository;
import com.taceiq.repository.ConfigurationDefinitionRepository;
import com.taceiq.repository.IngestedSourceRecordRepository;
import com.taceiq.repository.InvestigationRepository;
import com.taceiq.security.AuthorizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
public class GraphQueryService {

    private final GraphQueryRepository queryRepo;
    private final GraphReadinessService readinessService;
    private final AuthorizationService authorizationService;
    private final ConfigurationRepository configurationRepository;
    private final ConfigurationDefinitionRepository definitionRepository;
    private final InvestigationRepository investigationRepository;
    private final CanonicalEvidenceRepository canonicalEvidenceRepository;
    private final IngestedSourceRecordRepository ingestedSourceRecordRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public GraphQueryService(GraphQueryRepository queryRepo, GraphReadinessService readinessService, AuthorizationService authorizationService,
                             ConfigurationRepository configurationRepository, ConfigurationDefinitionRepository definitionRepository,
                             InvestigationRepository investigationRepository, CanonicalEvidenceRepository canonicalEvidenceRepository,
                             IngestedSourceRecordRepository ingestedSourceRecordRepository) {
        this.queryRepo = queryRepo;
        this.readinessService = readinessService;
        this.authorizationService = authorizationService;
        this.configurationRepository = configurationRepository;
        this.definitionRepository = definitionRepository;
        this.investigationRepository = investigationRepository;
        this.canonicalEvidenceRepository = canonicalEvidenceRepository;
        this.ingestedSourceRecordRepository = ingestedSourceRecordRepository;
    }

    public GraphQueryService(GraphQueryRepository queryRepo, GraphReadinessService readinessService, AuthorizationService authorizationService,
                             ConfigurationRepository configurationRepository, ConfigurationDefinitionRepository definitionRepository,
                             InvestigationRepository investigationRepository, CanonicalEvidenceRepository canonicalEvidenceRepository) {
        this(queryRepo, readinessService, authorizationService, configurationRepository, definitionRepository, investigationRepository, canonicalEvidenceRepository, null);
    }

    // Backward compat for tests that construct with 4 args (without InvestigationRepository, Canonical)
    public GraphQueryService(GraphQueryRepository queryRepo, GraphReadinessService readinessService, AuthorizationService authorizationService,
                             ConfigurationRepository configurationRepository, ConfigurationDefinitionRepository definitionRepository) {
        this(queryRepo, readinessService, authorizationService, configurationRepository, definitionRepository, null, null);
    }

    // Backward compat 6 args (without Canonical)
    public GraphQueryService(GraphQueryRepository queryRepo, GraphReadinessService readinessService, AuthorizationService authorizationService,
                             ConfigurationRepository configurationRepository, ConfigurationDefinitionRepository definitionRepository,
                             InvestigationRepository investigationRepository) {
        this(queryRepo, readinessService, authorizationService, configurationRepository, definitionRepository, investigationRepository, null);
    }

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int DEPTH_CEILING = 10;

    public GraphEvidencePageResponse searchEvidence(String caseId, String actorId, Integer page, Integer size) {
        return searchEvidence(caseId, actorId, null, page, size, null);
    }

    public GraphEvidencePageResponse searchEvidence(String caseId, String actorId, Long incidentId, Integer page, Integer size) {
        return searchEvidence(caseId, actorId, incidentId, page, size, null);
    }

    public GraphEvidencePageResponse searchEvidence(String caseId, String actorId, Long incidentId, Integer page, Integer size, Long excludeLinkedIncidentId) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        // Evidence list is PostgreSQL-authoritative – do NOT require Neo4j graphReady
        // Neo4j is only for relationships/traceability

        int p = page != null ? page : DEFAULT_PAGE;
        int s = size != null ? size : DEFAULT_SIZE;
        if (p < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >=0");
        if (s <= 0 || s > MAX_SIZE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be 1-100");
        String cId = caseId != null && !caseId.isBlank() ? caseId.trim() : null;
        String aId = actorId != null && !actorId.isBlank() ? actorId.trim() : null;

        // If canonical repo not available (tests), fallback to Neo4j
        if (canonicalEvidenceRepository == null) {
            ensureGraphReady(orgId);
            long total = queryRepo.countEvidence(orgId, cId, aId);
            int totalPages = (int) Math.ceil((double) total / s);
            int skip = p * s;
            var content = queryRepo.findEvidence(orgId, cId, aId, skip, s);
            return GraphEvidencePageResponse.builder()
                    .content(content)
                    .page(p).size(s)
                    .totalElements(total).totalPages(totalPages)
                    .build();
        }

        // PostgreSQL path – incident-first
        if (incidentId != null) {
            // Incident-scoped: use investigation evidence union (canonical incident_id + investigation_evidence join)
            // For graph evidence endpoint, we expose incident evidence via canonical incident_id for simplicity
            // Use paginated in-memory for incident (bounded)
            var allForIncident = canonicalEvidenceRepository.findByIncidentIdAndOrganisationOrgId(incidentId, orgId).stream()
                    .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                    .toList();
            // Also include via investigation_evidence join? For graph endpoint we keep simple: only direct canonical for now
            // Filter by caseId/actorId if provided
            var filtered = allForIncident.stream().filter(c -> {
                if (cId != null && !cId.equals(c.getCaseId())) return false;
                if (aId != null && !aId.equals(c.getActorId())) return false;
                return true;
            }).toList();
            long total = filtered.size();
            int totalPages = (int) Math.ceil((double) total / s);
            int from = Math.min(p * s, filtered.size());
            int to = Math.min(from + s, filtered.size());
            var pageContent = filtered.subList(from, to).stream().map(this::toGraphResponse).toList();
            return GraphEvidencePageResponse.builder()
                    .content(pageContent)
                    .page(p).size(s)
                    .totalElements(total).totalPages(totalPages)
                    .build();
        }

        // Org-wide evidence: query IngestedSourceRecord when browsing without incident context
        PageRequest pageable = PageRequest.of(p, s, Sort.by("id").ascending());
        if (excludeLinkedIncidentId != null) {
            Page<CanonicalEvidence> pg = canonicalEvidenceRepository.findAvailableForOrganisationAndInvestigation(orgId, cId, aId, excludeLinkedIncidentId, pageable);
            long total = canonicalEvidenceRepository.countAvailableForOrganisationAndInvestigation(orgId, cId, aId, excludeLinkedIncidentId);
            int totalPages = (int) Math.ceil((double) total / s);
            var content = pg.getContent().stream().map(this::toGraphResponse).toList();
            return GraphEvidencePageResponse.builder()
                    .content(content)
                    .page(p).size(s)
                    .totalElements(total).totalPages(totalPages)
                    .build();
        } else if (cId != null || aId != null) {
            Page<CanonicalEvidence> pg = canonicalEvidenceRepository.findByOrgAndCaseAndActor(orgId, cId, aId, pageable);
            long total = canonicalEvidenceRepository.countByOrgAndCaseAndActor(orgId, cId, aId);
            int totalPages = (int) Math.ceil((double) total / s);
            var content = pg.getContent().stream().map(this::toGraphResponse).toList();
            return GraphEvidencePageResponse.builder()
                    .content(content)
                    .page(p).size(s)
                    .totalElements(total).totalPages(totalPages)
                    .build();
        } else if (ingestedSourceRecordRepository != null) {
            Page<IngestedSourceRecord> pg = ingestedSourceRecordRepository.findByOrganisationOrgId(orgId, pageable);
            if (pg != null && pg.getTotalElements() > 0) {
                long total = pg.getTotalElements();
                int totalPages = pg.getTotalPages();
                var content = pg.getContent().stream().map(this::toGraphResponse).toList();
                return GraphEvidencePageResponse.builder()
                        .content(content)
                        .page(p).size(s)
                        .totalElements(total).totalPages(totalPages)
                        .build();
            } else if (canonicalEvidenceRepository != null) {
                Page<CanonicalEvidence> cpg = canonicalEvidenceRepository.findByOrganisationOrgIdAndIsDeletedFalse(orgId, pageable);
                long total = canonicalEvidenceRepository.countByOrganisationOrgIdAndIsDeletedFalse(orgId);
                int totalPages = (int) Math.ceil((double) total / s);
                var content = cpg != null ? cpg.getContent().stream().map(this::toGraphResponse).toList() : java.util.List.<GraphEvidenceResponse>of();
                return GraphEvidencePageResponse.builder()
                        .content(content)
                        .page(p).size(s)
                        .totalElements(total).totalPages(totalPages)
                        .build();
            } else {
                return GraphEvidencePageResponse.builder()
                        .content(java.util.List.of())
                        .page(p).size(s)
                        .totalElements(0)
                        .totalPages(0)
                        .build();
            }
        } else {
            Page<CanonicalEvidence> pg = canonicalEvidenceRepository.findByOrganisationOrgIdAndIsDeletedFalse(orgId, pageable);
            long total = canonicalEvidenceRepository.countByOrganisationOrgIdAndIsDeletedFalse(orgId);
            int totalPages = (int) Math.ceil((double) total / s);
            var content = pg.getContent().stream().map(this::toGraphResponse).toList();
            return GraphEvidencePageResponse.builder()
                    .content(content)
                    .page(p).size(s)
                    .totalElements(total).totalPages(totalPages)
                    .build();
        }
    }

    private GraphEvidenceResponse toGraphResponse(IngestedSourceRecord r) {
        String stableId = "SRC_" + r.getSourceType() + "_" + r.getSourceRecordId();
        stableId = stableId.trim().replaceAll("\\s+", "_");
        String timeStr = r.getIngestedAt() != null ? r.getIngestedAt().toString() : null;
        return GraphEvidenceResponse.builder()
                .stableId(stableId)
                .title(r.getSourceRecordId())
                .sourceType(r.getSourceType())
                .status("READY")
                .sourceCreatedAt(timeStr)
                .sourceUpdatedAt(timeStr)
                .batchReference(r.getBatchReference())
                .sourceRecordId(r.getSourceRecordId())
                .build();
    }

    private GraphEvidenceResponse toGraphResponse(CanonicalEvidence c) {
        String batchRef = null;
        try {
            if (c.getIncident() != null) {
                batchRef = c.getIncident().getBatchReference();
            }
        } catch (Exception ignored) {}

        return GraphEvidenceResponse.builder()
                .stableId(c.getExternalId())
                .title(c.getTitle())
                .sourceType(c.getSourceType())
                .status(c.getStatus())
                .sourceCreatedAt(c.getSourceCreatedAt() != null ? c.getSourceCreatedAt().toString() : null)
                .sourceUpdatedAt(c.getSourceUpdatedAt() != null ? c.getSourceUpdatedAt().toString() : null)
                .batchReference(batchRef)
                .sourceRecordId(c.getExternalId())
                .build();
    }

    public CaseTraceabilityResponse traceCase(String caseId) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireTraceabilityAccess();
        ensureGraphReady(orgId);
        if (caseId == null || caseId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "caseId is required");
        String cId = caseId.trim();
        // check existence tenant-isolated
        if (!queryRepo.caseExists(orgId, cId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found with stableId: " + cId);
        }
        int depth = resolveDepth(orgId);
        return queryRepo.traceCase(orgId, cId, depth);
    }

    public IncidentTraceabilityResponse traceIncident(Long incidentId) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireTraceabilityAccess();
        ensureGraphReady(orgId);
        if (incidentId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "incidentId is required");
        // Verify incident belongs to current organisation (tenant isolation)
        if (investigationRepository != null) {
            var incident = investigationRepository.findByIdAndOrganisationOrgId(incidentId, orgId).orElse(null);
            if (incident == null) {
                // Check if exists cross-tenant to return proper authorization/not-found response
                var existsAny = investigationRepository.findById(incidentId);
                if (existsAny.isPresent()) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found with id: " + incidentId);
                }
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found with id: " + incidentId);
            }
        }
        // Check existence in graph (tenant-scoped)
        if (!queryRepo.incidentExists(orgId, incidentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found with id: " + incidentId);
        }
        int depth = resolveDepth(orgId);
        return queryRepo.traceIncident(orgId, incidentId, depth);
    }

    private void ensureGraphReady(Long orgId) {
        if (!readinessService.isOrgGraphReady(orgId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Graph not ready for organisation " + orgId + " – ensure canonical sync SUCCESS and projection validated");
        }
    }

    private int resolveDepth(Long orgId) {
        int configured = DEPTH_CEILING;
        try {
            var defOpt = definitionRepository.findByKey("TRACEABILITY_DEPTH");
            if (defOpt.isPresent()) {
                String defDefault = defOpt.get().getDefaultValue();
                int defVal = defDefault != null ? Integer.parseInt(defDefault.trim()) : 5;
                configured = defVal;
                var cfgOpt = configurationRepository.findByDefinitionKeyAndOrganisationOrgId("TRACEABILITY_DEPTH", orgId);
                if (cfgOpt.isPresent() && cfgOpt.get().getValue() != null && !cfgOpt.get().getValue().isBlank()) {
                    configured = Integer.parseInt(cfgOpt.get().getValue().trim());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to resolve TRACEABILITY_DEPTH for org {}: {}", orgId, e.getMessage());
            configured = 5;
        }
        if (configured <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TRACEABILITY_DEPTH must be positive");
        return Math.min(configured, DEPTH_CEILING);
    }
}
