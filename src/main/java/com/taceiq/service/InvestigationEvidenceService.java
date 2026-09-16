package com.taceiq.service;

import com.taceiq.dto.InvestigationEvidenceResponse;
import com.taceiq.dto.EvidenceMatchExplanation;
import com.taceiq.dto.BatchConnectionMapResponse;
import com.taceiq.dto.InvestigationEvidenceSummaryResponse;
import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.EvidenceCorrelationProvenance;
import com.taceiq.entity.Investigation;
import com.taceiq.entity.InvestigationEvidence;
import com.taceiq.entity.InvestigationEvidenceAssessment;
import com.taceiq.dto.InvestigationEvidenceAssessmentRequest;
import com.taceiq.graph.service.GraphReadinessService;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.IngestedSourceRecordRepository;
import com.taceiq.repository.InvestigationEvidenceRepository;
import com.taceiq.repository.InvestigationRepository;
import com.taceiq.repository.InvestigationEvidenceAssessmentRepository;
import com.taceiq.repository.EvidenceCorrelationProvenanceRepository;
import com.taceiq.security.AuthorizationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class InvestigationEvidenceService {

    private final InvestigationRepository investigationRepository;
    private final CanonicalEvidenceRepository canonicalRepo;
    private final InvestigationEvidenceRepository linkRepository;
    private final AuthorizationService authorizationService;
    private final GraphReadinessService graphReadinessService;
    private final EvidenceCorrelationProvenanceRepository provenanceRepository;
    private final IngestedSourceRecordRepository sourceRecordRepository;
    private final InvestigationEvidenceAssessmentRepository assessmentRepository;

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    @org.springframework.beans.factory.annotation.Autowired
    public InvestigationEvidenceService(InvestigationRepository investigationRepository,
                                        CanonicalEvidenceRepository canonicalRepo,
                                        InvestigationEvidenceRepository linkRepository,
                                        AuthorizationService authorizationService,
                                        GraphReadinessService graphReadinessService,
                                        EvidenceCorrelationProvenanceRepository provenanceRepository,
                                        IngestedSourceRecordRepository sourceRecordRepository,
                                        InvestigationEvidenceAssessmentRepository assessmentRepository) {
        this.investigationRepository = investigationRepository;
        this.canonicalRepo = canonicalRepo;
        this.linkRepository = linkRepository;
        this.authorizationService = authorizationService;
        this.graphReadinessService = graphReadinessService;
        this.provenanceRepository = provenanceRepository;
        this.sourceRecordRepository = sourceRecordRepository;
        this.assessmentRepository = assessmentRepository;
    }

    // Backward compat for tests with 5 args
    public InvestigationEvidenceService(InvestigationRepository investigationRepository,
                                        CanonicalEvidenceRepository canonicalRepo,
                                        InvestigationEvidenceRepository linkRepository,
                                        AuthorizationService authorizationService,
                                        GraphReadinessService graphReadinessService) {
                        this(investigationRepository, canonicalRepo, linkRepository, authorizationService, graphReadinessService, null, null, null);
    }

    public InvestigationEvidenceService(InvestigationRepository investigationRepository,
                                        CanonicalEvidenceRepository canonicalRepo,
                                        InvestigationEvidenceRepository linkRepository,
                                        AuthorizationService authorizationService,
                                        GraphReadinessService graphReadinessService,
                                        EvidenceCorrelationProvenanceRepository provenanceRepository) {
        this(investigationRepository, canonicalRepo, linkRepository, authorizationService, graphReadinessService, provenanceRepository, null, null);
    }

    public InvestigationEvidenceService(InvestigationRepository investigationRepository,
                                        CanonicalEvidenceRepository canonicalRepo,
                                        InvestigationEvidenceRepository linkRepository,
                                        AuthorizationService authorizationService,
                                        GraphReadinessService graphReadinessService,
                                        EvidenceCorrelationProvenanceRepository provenanceRepository,
                                        IngestedSourceRecordRepository sourceRecordRepository) {
        this(investigationRepository, canonicalRepo, linkRepository, authorizationService, graphReadinessService, provenanceRepository, sourceRecordRepository, null);
    }

    private Investigation loadInvestigation(Long investigationId) {
        Long orgId = authorizationService.getCurrentOrgId();
        return investigationRepository.findByIdAndOrganisationOrgId(investigationId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Investigation not found with id: " + investigationId));
    }

    private void ensureGraphReady(Long orgId) {
        if (!graphReadinessService.isOrgGraphReady(orgId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Graph not ready for organisation " + orgId);
        }
    }

    private void ensureMutable(Investigation inv) {
        String status = inv.getStatus();
        if ("COMPLETED".equals(status) || "ARCHIVED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Investigation is " + status + " and cannot be modified");
        }
        if (!"DRAFT".equals(status) && !"ACTIVE".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Investigation status " + status + " does not allow evidence linking");
        }
    }

    private InvestigationEvidenceAssessment findAssessment(Long orgId, Long investigationId, Long evidenceId) {
        if (assessmentRepository == null || evidenceId == null) return null;
        return assessmentRepository.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(orgId, investigationId, evidenceId).orElse(null);
    }

    private InvestigationEvidenceResponse withAssessment(InvestigationEvidenceResponse response, Long orgId, Long investigationId, Long evidenceId) {
        InvestigationEvidenceAssessment assessment = findAssessment(orgId, investigationId, evidenceId);
        if (assessment == null) return response;
        return response.toBuilder()
            .reviewStatus(canonicalValue(assessment.getReviewStatus()))
            .relevance(canonicalValue(assessment.getRelevance()))
            .importance(canonicalValue(assessment.getImportance()))
            .assessment(canonicalValue(assessment.getAssessment()))
                .investigatorNotes(assessment.getInvestigatorNotes())
                .reviewedByUserId(assessment.getReviewedBy() != null ? assessment.getReviewedBy().getId() : null)
                .reviewedAt(assessment.getReviewedAt())
                .build();
    }

    @Transactional
    public InvestigationEvidenceResponse linkEvidence(Long investigationId, String stableId) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        ensureGraphReady(orgId);
        Investigation inv = loadInvestigation(investigationId);
        ensureMutable(inv);

        String sid = stableId != null ? stableId.trim() : null;
        if (sid == null || sid.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stableId is required");

        CanonicalEvidence evidence = canonicalRepo.findByExternalIdAndOrganisationOrgId(sid, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found with stableId: " + sid));
        if (Boolean.TRUE.equals(evidence.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found with stableId: " + sid);
        }

        if (linkRepository.existsByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(orgId, investigationId, evidence.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Evidence already linked to investigation");
        }

        InvestigationEvidence link = InvestigationEvidence.builder()
                .organisation(inv.getOrganisation())
                .investigation(inv)
                .canonicalEvidence(evidence)
                .createdBy(authorizationService.getCurrentUser())
                .build();
        InvestigationEvidence saved = linkRepository.save(link);
        return toResponse(saved, orgId, investigationId);
    }

    @Transactional
    public void unlinkEvidence(Long investigationId, String stableId) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        ensureGraphReady(orgId);
        Investigation inv = loadInvestigation(investigationId);
        ensureMutable(inv);
        String sid = stableId != null ? stableId.trim() : null;
        if (sid == null || sid.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stableId is required");

        CanonicalEvidence evidence = canonicalRepo.findByExternalIdAndOrganisationOrgId(sid, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found with stableId: " + sid));

        InvestigationEvidence link = linkRepository.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(orgId, investigationId, evidence.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Link not found for evidence " + sid + " and investigation " + investigationId));

        linkRepository.delete(link);
        // Do not delete canonical evidence
    }

    public org.springframework.data.domain.Page<InvestigationEvidenceResponse> listEvidence(Long investigationId, Integer page, Integer size) {
        return listEvidence(investigationId, page, size, null, null, null, null);
    }

    public org.springframework.data.domain.Page<InvestigationEvidenceResponse> listEvidence(Long investigationId, Integer page, Integer size,
                                                                                             String search, String sourceType, String status, String sort) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        // Evidence listing is PostgreSQL-authoritative – do NOT require Neo4j graphReady
        Investigation inv = loadInvestigation(investigationId); // tenant check

        int p = page != null ? page : 0;
        int s = size != null ? size : DEFAULT_SIZE;
        if (p < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >=0");
        if (s < 1 || s > MAX_SIZE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be 1-100");

        // Validate sort allowlist
        String sortField = "stableId";
        boolean sortAsc = true;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.trim().split(",");
            String field = parts[0].trim();
            Set<String> allowed = Set.of("stableId", "title", "sourceType", "status", "linkedAt");
            if (!allowed.contains(field)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort field. Allowed: " + allowed);
            }
            sortField = field;
            if (parts.length > 1) {
                String dir = parts[1].trim().toLowerCase();
                if ("desc".equals(dir)) sortAsc = false;
                else if (!"asc".equals(dir)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort direction. Use asc or desc");
            }
        }

        // Phase 5: Incident-scoped evidence — union of:
        // 1) canonical_evidence where incident_id = incidentId (direct association via Phase 1)
        // 2) investigation_evidence join table (explicit links, legacy)
        // Do not duplicate, preserve both, tenant-safe, do not leak cross-org, do not silently backfill legacy caseId.
        // Existing DTO reused: InvestigationEvidenceResponse (stableId/title/sourceType/status/linkedAt)
        // Phase 9: add search/filter
        String searchLower = search != null && !search.isBlank() ? search.trim().toLowerCase() : null;
        String filterSourceType = sourceType != null && !sourceType.isBlank() ? sourceType.trim() : null;
        String filterStatus = status != null && !status.isBlank() ? status.trim() : null;
        List<EvidenceCorrelationProvenance> incidentProvenance = provenanceRepository == null
            ? List.of() : provenanceRepository.findByOrganisationOrgIdAndInvestigationId(orgId, investigationId);

        // 1) Direct incident evidence (canonical.incident_id)
        List<CanonicalEvidence> directCanonical = Collections.emptyList();
        try {
            directCanonical = canonicalRepo.findByIncidentIdAndOrganisationOrgId(investigationId, orgId).stream()
                    .filter(ce -> !Boolean.TRUE.equals(ce.getIsDeleted()))
                    .collect(Collectors.toList());
        } catch (Exception ignored) {}

        // 2) Explicit links
        List<InvestigationEvidence> linked = Collections.emptyList();
        try {
            linked = linkRepository.findByOrganisationOrgIdAndInvestigationId(orgId, investigationId, PageRequest.of(0, 1000, Sort.by("createdAt").ascending())).getContent();
        } catch (Exception ignored) {}

        // Merge by stableId (externalId), deduplicate: linked entries take precedence (preserve linkedAt), direct fills gaps
        Map<String, InvestigationEvidenceResponse> merged = new LinkedHashMap<>();

        for (InvestigationEvidence link : linked) {
            CanonicalEvidence ce = link.getCanonicalEvidence();
            if (ce == null || Boolean.TRUE.equals(ce.getIsDeleted())) continue;
            if (ce.getOrganisation() != null && !ce.getOrganisation().getOrgId().equals(orgId)) continue;
            String key = ce.getExternalId() != null ? ce.getExternalId() : String.valueOf(ce.getId());
            merged.put(key, withAssessment(toResponse(link, orgId, investigationId, incidentProvenance), orgId, investigationId, ce.getId()));
        }
        for (CanonicalEvidence ce : directCanonical) {
            String key = ce.getExternalId() != null ? ce.getExternalId() : String.valueOf(ce.getId());
            if (merged.containsKey(key)) continue;
            if (ce.getOrganisation() != null && !ce.getOrganisation().getOrgId().equals(orgId)) continue;
            merged.put(key, withAssessment(fromCanonical(ce, orgId, investigationId, incidentProvenance), orgId, investigationId, ce.getId()));
        }

        List<InvestigationEvidenceResponse> filtered = new ArrayList<>(merged.values());

        // Apply search and filters (incident-scoped, tenant-safe already)
        if (searchLower != null) {
            filtered = filtered.stream().filter(r ->
                    (r.getStableId() != null && r.getStableId().toLowerCase().contains(searchLower)) ||
                    (r.getTitle() != null && r.getTitle().toLowerCase().contains(searchLower)) ||
                    (r.getSourceType() != null && r.getSourceType().toLowerCase().contains(searchLower)) ||
                    (r.getStatus() != null && r.getStatus().toLowerCase().contains(searchLower))
            ).collect(Collectors.toList());
        }
        if (filterSourceType != null) {
            filtered = filtered.stream().filter(r -> filterSourceType.equalsIgnoreCase(r.getSourceType())).collect(Collectors.toList());
        }
        if (filterStatus != null) {
            filtered = filtered.stream().filter(r -> filterStatus.equalsIgnoreCase(r.getStatus())).collect(Collectors.toList());
        }

        // Deterministic sorting with allowlist
        Comparator<InvestigationEvidenceResponse> comparator;
        switch (sortField) {
            case "title":
                comparator = Comparator.comparing(InvestigationEvidenceResponse::getTitle, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
            case "sourceType":
                comparator = Comparator.comparing(InvestigationEvidenceResponse::getSourceType, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
            case "status":
                comparator = Comparator.comparing(InvestigationEvidenceResponse::getStatus, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
            case "linkedAt":
                comparator = Comparator.comparing(InvestigationEvidenceResponse::getLinkedAt, Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "stableId":
            default:
                comparator = Comparator.comparing(InvestigationEvidenceResponse::getStableId, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
        }
        if (!sortAsc) comparator = comparator.reversed();
        // Secondary deterministic tie-breaker stableId
        comparator = comparator.thenComparing(InvestigationEvidenceResponse::getStableId, Comparator.nullsLast(String::compareTo));
        filtered.sort(comparator);

        // Manual pagination (like Timeline) — avoid unbounded load, already bounded 1000
        long total = filtered.size();
        int totalPages = (int) Math.ceil((double) total / s);
        int from = Math.min(p * s, filtered.size());
        int to = Math.min(from + s, filtered.size());
        List<InvestigationEvidenceResponse> pageContent = filtered.subList(from, to);

        return new PageImpl<>(pageContent, PageRequest.of(p, s), total);
    }

    private InvestigationEvidenceResponse fromCanonical(CanonicalEvidence ce, Long orgId, Long investigationId) {
        return fromCanonical(ce, orgId, investigationId, null);
    }

    private InvestigationEvidenceResponse fromCanonical(CanonicalEvidence ce, Long orgId, Long investigationId,
                                                        List<EvidenceCorrelationProvenance> incidentProvenance) {
        // For direct incident evidence without explicit link, linkedAt is derived from canonical timestamps
        java.time.Instant linkedAt = ce.getLastSeenAt() != null ? ce.getLastSeenAt() : ce.getFirstSeenAt();
        if (linkedAt == null) linkedAt = ce.getCreatedAt();
        String corr = null; String batch = null; String recId = null;
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(ce.getNormalizedPayload() != null ? ce.getNormalizedPayload() : "{}");
            if (node.has("correlationReason")) corr = node.get("correlationReason").asText();
            batch = extractStoredField(ce.getNormalizedPayload(), "batch_id", "batch_reference", "batchReference", "batch");
            recId = extractStoredField(ce.getNormalizedPayload(), "source_record_id", "sourceRecordId", "record_id");
            if (recId == null && ce.getExternalId() != null) recId = ce.getExternalId();
        } catch (Exception ignored) {}
        List<EvidenceMatchExplanation> explanations = explanationsFor(orgId, investigationId, ce, incidentProvenance);
        return InvestigationEvidenceResponse.builder()
                .stableId(ce.getExternalId())
                .title(ce.getTitle())
                .sourceType(ce.getSourceType())
                .status(ce.getStatus())
                .linkedAt(linkedAt)
                .correlationReason(corr)
                .batchReference(batch)
                .sourceRecordId(recId)
                .machineReference(extractStoredField(ce.getNormalizedPayload(), "machine_id", "machine_reference", "machineReference"))
                .supplierReference(extractStoredField(ce.getNormalizedPayload(), "supplier_id", "supplier_reference", "supplierReference"))
                .productReference(extractStoredField(ce.getNormalizedPayload(), "product_id", "product_reference", "productReference"))
                .orderReference(extractStoredField(ce.getNormalizedPayload(), "order_id", "order_reference", "orderReference"))
                .originalFileName(extractField(ce.getNormalizedPayload(), "originalName"))
                .fileId(extractLongField(ce.getNormalizedPayload(), "fileId"))
                .contentType(extractField(ce.getNormalizedPayload(), "contentType"))
                .size(extractLongField(ce.getNormalizedPayload(), "size"))
                .associationType(explanations.isEmpty() ? "INCIDENT_ASSOCIATED" : "AUTOMATICALLY_DISCOVERED")
                .matchExplanations(explanations)
                .normalizedPayload(ce.getNormalizedPayload())
                .build();
    }

    private InvestigationEvidenceResponse toResponse(InvestigationEvidence link, Long orgId, Long investigationId) {
        return toResponse(link, orgId, investigationId, null);
    }

    private InvestigationEvidenceResponse toResponse(InvestigationEvidence link, Long orgId, Long investigationId,
                                                     List<EvidenceCorrelationProvenance> incidentProvenance) {
        CanonicalEvidence ce = link.getCanonicalEvidence();
        List<EvidenceMatchExplanation> explanations = explanationsFor(orgId, investigationId, ce, incidentProvenance);
        String corr = explanations.isEmpty() ? extractCorrelation(ce.getNormalizedPayload()) : explanations.get(0).getReason();
        return InvestigationEvidenceResponse.builder()
                .stableId(ce.getExternalId())
                .title(ce.getTitle())
                .sourceType(ce.getSourceType())
                .status(ce.getStatus())
                .linkedAt(link.getCreatedAt())
                .correlationReason(corr)
                .batchReference(extractStoredField(ce.getNormalizedPayload(), "batch_id", "batch_reference", "batchReference", "batch"))
                .sourceRecordId(extractStoredField(ce.getNormalizedPayload(), "source_record_id", "sourceRecordId", "record_id"))
                .machineReference(extractStoredField(ce.getNormalizedPayload(), "machine_id", "machine_reference", "machineReference"))
                .supplierReference(extractStoredField(ce.getNormalizedPayload(), "supplier_id", "supplier_reference", "supplierReference"))
                .productReference(extractStoredField(ce.getNormalizedPayload(), "product_id", "product_reference", "productReference"))
                .orderReference(extractStoredField(ce.getNormalizedPayload(), "order_id", "order_reference", "orderReference"))
                .originalFileName(extractField(ce.getNormalizedPayload(), "originalName"))
                .fileId(extractLongField(ce.getNormalizedPayload(), "fileId"))
                .contentType(extractField(ce.getNormalizedPayload(), "contentType"))
                .size(extractLongField(ce.getNormalizedPayload(), "size"))
                .associationType(explanations.isEmpty() ? "MANUALLY_LINKED" : "AUTOMATICALLY_DISCOVERED")
                .matchExplanations(explanations)
                .normalizedPayload(ce.getNormalizedPayload())
                .build();
    }

    private List<EvidenceMatchExplanation> explanationsFor(Long orgId, Long investigationId, CanonicalEvidence evidence) {
        return explanationsFor(orgId, investigationId, evidence, null);
        }

        private List<EvidenceMatchExplanation> explanationsFor(Long orgId, Long investigationId, CanonicalEvidence evidence,
                                   List<EvidenceCorrelationProvenance> incidentProvenance) {
        if (evidence == null) return List.of();
        if (evidence.getId() != null) {
            List<EvidenceCorrelationProvenance> provenance = incidentProvenance != null
                ? incidentProvenance.stream().filter(item -> evidence.getId().equals(item.getCanonicalEvidence().getId())).toList()
                : provenanceRepository == null ? List.of() : provenanceRepository
                .findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(orgId, investigationId, evidence.getId());
                List<EvidenceMatchExplanation> stored = provenance.stream().map(this::toExplanation).toList();
            if (!stored.isEmpty()) return stored;
        }
        return legacyExplanation(evidence);
    }

    private List<EvidenceMatchExplanation> legacyExplanation(CanonicalEvidence evidence) {
        String payload = evidence.getNormalizedPayload();
        String reason = extractCorrelation(payload);
        if (reason == null || reason.isBlank()) return List.of();
        String batch = extractStoredField(payload, "batch_id", "batch_reference", "batchReference", "batch");
        String sourceRecord = extractStoredField(payload, "source_record_id", "sourceRecordId", "record_id");
        if (sourceRecord == null) sourceRecord = evidence.getExternalId();
        String matchedField = null;
        String matchedValue = null;
        String intermediateType = null;
        String intermediateValue = null;
        if ("DIRECT_BATCH_MATCH".equals(reason)) {
            matchedField = "batch_reference";
            matchedValue = batch;
        } else if ("MACHINE_MATCH_FROM_BATCH".equals(reason)) {
            matchedField = "machine_reference";
            matchedValue = extractStoredField(payload, "machine_id", "machine_reference", "machineReference");
            intermediateType = "machine";
            intermediateValue = matchedValue;
        } else if ("SUPPLIER_MATCH_FROM_BATCH".equals(reason)) {
            matchedField = "supplier_reference";
            matchedValue = extractStoredField(payload, "supplier_id", "supplier_reference", "supplierReference");
            intermediateType = "supplier";
            intermediateValue = matchedValue;
        } else if ("PRODUCT_MATCH_FROM_BATCH".equals(reason)) {
            matchedField = "product_reference";
            matchedValue = extractStoredField(payload, "product_id", "product_reference", "productReference");
            intermediateType = "product";
            intermediateValue = matchedValue;
        }
        List<String> path = new java.util.ArrayList<>();
        String incidentLabel = evidence.getIncident() != null && evidence.getIncident().getInvestigationKey() != null
                ? evidence.getIncident().getInvestigationKey()
                : evidence.getIncident() != null && evidence.getIncident().getId() != null
                ? String.valueOf(evidence.getIncident().getId()) : "unknown";
        path.add("Incident:" + incidentLabel);
        if (batch != null) path.add("Batch:" + batch);
        if (intermediateType != null && intermediateValue != null) {
            path.add(Character.toUpperCase(intermediateType.charAt(0)) + intermediateType.substring(1) + ":" + intermediateValue);
        }
        path.add((evidence.getSourceType() != null ? evidence.getSourceType() : "Evidence") + " Evidence:" + sourceRecord);
        return List.of(EvidenceMatchExplanation.builder()
                .reason(reason)
                .matchedField(matchedField)
                .matchedValue(matchedValue)
                .sourceRecordId(sourceRecord)
                .intermediateEntityType(intermediateType)
                .intermediateEntityValue(intermediateValue)
                .connectionPath(path)
                .discoveredAt(evidence.getLastSeenAt())
                .build());
    }

    private EvidenceMatchExplanation toExplanation(EvidenceCorrelationProvenance provenance) {
        List<String> path = List.of();
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(provenance.getConnectionPath());
            if (node.isArray()) {
                var values = new java.util.ArrayList<String>();
                node.forEach(value -> {
                    String val = value.asText();
                    if (!val.startsWith("Incident:")) {
                        values.add(val);
                    }
                });
                path = values;
            }
        } catch (Exception ignored) {}
        return EvidenceMatchExplanation.builder()
                .reason(provenance.getReason())
                .matchedField(provenance.getMatchedField())
                .matchedValue(provenance.getMatchedValue())
                .sourceRecordId(provenance.getSourceRecordId())
                .intermediateEntityType(provenance.getIntermediateEntityType())
                .intermediateEntityValue(provenance.getIntermediateEntityValue())
                .connectionPath(path)
                .discoveredAt(provenance.getDiscoveredAt())
                .build();
    }

    private static String extractCorrelation(String payload) {
        return extractField(payload, "correlationReason");
    }

    private static String extractField(String payload, String field) {
        if (payload == null) return null;
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);
            if (node.has(field) && !node.get(field).isNull()) return node.get(field).asText();
        } catch (Exception ignored) {}
        return null;
    }

    private static String extractStoredField(String payload, String... aliases) {
        if (payload == null) return null;
        try {
            var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);
            String value = findNodeValue(root, aliases);
            if (value != null) return value;
            var original = root.get("originalPayload");
            if (original != null && original.isTextual()) {
                value = findNodeValue(new com.fasterxml.jackson.databind.ObjectMapper().readTree(original.asText()), aliases);
                if (value != null) return value;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String findNodeValue(com.fasterxml.jackson.databind.JsonNode node, String... aliases) {
        if (node == null || !node.isObject()) return null;
        for (var field : node.properties()) {
            String normalized = field.getKey().toLowerCase().replaceAll("[_\\s]+", "");
            for (String alias : aliases) {
                if (normalized.equals(alias.toLowerCase().replaceAll("[_\\s]+", "")) && !field.getValue().isNull()) {
                    return field.getValue().asText();
                }
            }
        }
        return null;
    }

    private static Long extractLongField(String payload, String field) {
        String value = extractField(payload, field);
        if (value == null) return null;
        try { return Long.valueOf(value); } catch (NumberFormatException ignored) { return null; }
    }

    public com.taceiq.dto.InvestigationEvidenceResponse getEvidenceDetail(Long investigationId, String stableId) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        // Detail is PostgreSQL-authoritative – do NOT require graphReady
        loadInvestigation(investigationId);
        String sid = stableId != null ? stableId.trim() : null;
        if (sid == null || sid.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stableId is required");
        Optional<CanonicalEvidence> ceOpt = canonicalRepo.findByExternalIdAndOrganisationOrgId(sid, orgId);
        if (ceOpt.isEmpty()) {
            ceOpt = canonicalRepo.findAllByOrganisationOrgId(orgId).stream()
                    .filter(e -> !Boolean.TRUE.equals(e.getIsDeleted()))
                    .filter(e -> sid.equalsIgnoreCase(e.getExternalId()) ||
                                 sid.equalsIgnoreCase(extractStoredField(e.getNormalizedPayload(), "sourceRecordId", "source_record_id")) ||
                                 (e.getExternalId() != null && (e.getExternalId().endsWith("_" + sid) || e.getExternalId().endsWith(":" + sid))))
                    .findFirst();
        }
        CanonicalEvidence ce = ceOpt.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found with stableId: " + sid));
        if (Boolean.TRUE.equals(ce.getIsDeleted())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found with stableId: " + sid);
        // Must be incident-scoped: either via incident_id or via explicit link
        boolean belongs = false;
        if (ce.getIncident() != null && ce.getIncident().getId() != null && ce.getIncident().getId().equals(investigationId)) {
            belongs = true;
        } else {
            // Check explicit link
            if (linkRepository.existsByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(orgId, investigationId, ce.getId())) {
                belongs = true;
            }
            // Also check direct canonical without incident_id but with same org — if evidence is not incident-stamped, it is still discoverable via link only
            // For incident-scoped detail, require belongs
        }
        if (!belongs) {
            // For evidence with incident_id null but not linked, we consider it not part of this incident
            // However to preserve legacy behavior where evidence is org-wide, we still allow detail if evidence exists in org but not incident? No, for incident-scoped detail we require belongs
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found for incident " + investigationId + " with stableId: " + sid);
        }
        // Return as InvestigationEvidenceResponse (reusing DTO, safe fields only)
        // Try to find linkedAt from join if exists, else from canonical
        var linkOpt = linkRepository.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(orgId, investigationId, ce.getId());
        if (linkOpt.isPresent()) {
            return withAssessment(toResponse(linkOpt.get(), orgId, investigationId), orgId, investigationId, ce.getId());
        }
            return withAssessment(fromCanonical(ce, orgId, investigationId), orgId, investigationId, ce.getId());
    }

    public InvestigationEvidenceSummaryResponse getEvidenceSummary(Long investigationId) {
        authorizationService.requireEvidenceGraphAccess();
        loadInvestigation(investigationId);
        List<InvestigationEvidenceResponse> all = new ArrayList<>();
        int page = 0;
        Page<InvestigationEvidenceResponse> current;
        do {
            current = listEvidence(investigationId, page++, 100);
            all.addAll(current.getContent());
        } while (page < current.getTotalPages());
        Set<String> sources = all.stream().map(InvestigationEvidenceResponse::getSourceType)
                .filter(Objects::nonNull).collect(Collectors.toCollection(TreeSet::new));
        long reviewed = all.stream().filter(item -> "REVIEWED".equals(item.getReviewStatus())).count();
        return InvestigationEvidenceSummaryResponse.builder()
                .investigationId(investigationId)
                .totalEvidence(current.getTotalElements())
                .reviewed(reviewed)
                .pendingReview(current.getTotalElements() - reviewed)
                .sourceSystems(sources)
                .build();
    }

    @Transactional
    public InvestigationEvidenceResponse saveAssessment(Long investigationId, String stableId, InvestigationEvidenceAssessmentRequest request) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        Investigation investigation = loadInvestigation(investigationId);
        ensureMutable(investigation);
        if (assessmentRepository == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Evidence assessment persistence is unavailable");
        }
        String sid = stableId != null ? stableId.trim() : null;
        if (sid == null || sid.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stableId is required");
        CanonicalEvidence evidence = canonicalRepo.findByExternalIdAndOrganisationOrgId(sid, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found with stableId: " + sid));
        if (Boolean.TRUE.equals(evidence.getIsDeleted()) || !belongsToInvestigation(orgId, investigationId, evidence)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found for investigation " + investigationId + " with stableId: " + sid);
        }
        String reviewStatus = normalize(request.getReviewStatus(), "PENDING_REVIEW");
        String relevance = normalizeNullable(request.getRelevance());
        String importance = normalizeNullable(request.getImportance());
        String assessmentValue = normalizeNullable(request.getAssessment());
        if ("PENDING_REVIEW".equals(reviewStatus) && relevance != null && importance != null && assessmentValue != null) {
            reviewStatus = "REVIEWED";
        }
        validateOneOf("reviewStatus", reviewStatus, Set.of("PENDING_REVIEW", "REVIEWED"));
        validateOneOfNullable("relevance", relevance, Set.of("RELEVANT", "NOT_RELEVANT"));
        validateOneOfNullable("importance", importance, Set.of("HIGH", "MEDIUM", "LOW"));
        validateOneOfNullable("assessment", assessmentValue, Set.of("SUPPORTS_INVESTIGATION", "CONTRADICTS_INVESTIGATION", "CONTEXT_ONLY", "INCONCLUSIVE", "NOT_ASSESSED"));
        if ("REVIEWED".equals(reviewStatus) && (relevance == null || importance == null || assessmentValue == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reviewed evidence requires relevance, importance, and assessment");
        }
        InvestigationEvidenceAssessment saved = assessmentRepository
                .findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(orgId, investigationId, evidence.getId())
                .orElseGet(() -> InvestigationEvidenceAssessment.builder()
                        .organisation(investigation.getOrganisation())
                        .investigation(investigation)
                        .canonicalEvidence(evidence)
                        .build());
        saved.setReviewStatus(canonicalValue(reviewStatus));
        saved.setRelevance(canonicalValue(relevance));
        saved.setImportance(canonicalValue(importance));
        saved.setAssessment(canonicalValue(assessmentValue));
        saved.setInvestigatorNotes(request.getInvestigatorNotes() != null ? request.getInvestigatorNotes().trim() : null);
        saved.setReviewedBy("REVIEWED".equals(reviewStatus) ? authorizationService.getCurrentUser() : null);
        saved.setReviewedAt("REVIEWED".equals(reviewStatus) ? Instant.now() : null);
        assessmentRepository.save(saved);
        return getEvidenceDetail(investigationId, sid);
    }

    private static String canonicalValue(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean belongsToInvestigation(Long orgId, Long investigationId, CanonicalEvidence evidence) {
        return evidence.getIncident() != null && investigationId.equals(evidence.getIncident().getId())
                || linkRepository.existsByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(orgId, investigationId, evidence.getId());
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static void validateOneOf(String field, String value, Set<String> allowed) {
        if (!allowed.contains(value)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be one of " + allowed);
    }

    private static void validateOneOfNullable(String field, String value, Set<String> allowed) {
        if (value != null && !allowed.contains(value)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be one of " + allowed);
    }

    @Transactional(readOnly = true)
    public BatchConnectionMapResponse getBatchConnectionMap(Long investigationId) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        Investigation investigation = loadInvestigation(investigationId);
        String batch = investigation.getBatchReference();
        if (batch == null || batch.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incident batchReference is required for the connection map");
        }

        Map<String, InvestigationEvidenceResponse> evidenceById = new LinkedHashMap<>();
        List<EvidenceCorrelationProvenance> incidentProvenance = provenanceRepository == null
            ? List.of() : provenanceRepository.findByOrganisationOrgIdAndInvestigationId(orgId, investigationId);
        canonicalRepo.findByIncidentIdAndOrganisationOrgId(investigationId, orgId).stream()
                .filter(e -> !Boolean.TRUE.equals(e.getIsDeleted()))
            .forEach(e -> evidenceById.putIfAbsent(e.getExternalId(), fromCanonical(e, orgId, investigationId, incidentProvenance)));
        linkRepository.findByOrganisationOrgIdAndInvestigationId(orgId, investigationId,
                        PageRequest.of(0, 1000, Sort.by("createdAt").ascending()))
                .getContent().stream()
                .filter(link -> link.getCanonicalEvidence() != null && !Boolean.TRUE.equals(link.getCanonicalEvidence().getIsDeleted()))
                .forEach(link -> evidenceById.put(link.getCanonicalEvidence().getExternalId(), toResponse(link, orgId, investigationId, incidentProvenance)));

        // --- PRIMARY vs CROSS-BATCH classification ---
        // Direct identifiers are those explicitly referenced by THIS batch's MES records (batchReference == current batch)
        Set<String> directMachineIds = new LinkedHashSet<>();
        Set<String> directSupplierIds = new LinkedHashSet<>();
        Set<String> directProductIds = new LinkedHashSet<>();
        if (investigation.getProductReference() != null && !investigation.getProductReference().isBlank()) {
            directProductIds.add(investigation.getProductReference().trim());
        }
        for (InvestigationEvidenceResponse item : evidenceById.values()) {
            if (batch.equalsIgnoreCase(item.getBatchReference())) {
                if (item.getMachineReference() != null && !item.getMachineReference().isBlank()) directMachineIds.add(item.getMachineReference().trim());
                if (item.getSupplierReference() != null && !item.getSupplierReference().isBlank()) directSupplierIds.add(item.getSupplierReference().trim());
                if (item.getProductReference() != null && !item.getProductReference().isBlank()) directProductIds.add(item.getProductReference().trim());
            }
        }

        Map<String, InvestigationEvidenceResponse> primaryEvidence = new LinkedHashMap<>();
        Map<String, InvestigationEvidenceResponse> crossBatchEvidenceMap = new LinkedHashMap<>();
        for (InvestigationEvidenceResponse item : evidenceById.values()) {
            boolean isPrimary = isPrimaryEvidence(item, batch, directMachineIds, directSupplierIds);
            if (isPrimary) primaryEvidence.put(item.getStableId(), item);
            else crossBatchEvidenceMap.put(item.getStableId(), item);
        }

        // 1. Identify entities for PRIMARY graph only
        Set<String> directMachines = new LinkedHashSet<>();
        Set<String> directProducts = new LinkedHashSet<>(directProductIds);
        Set<String> directSuppliers = new LinkedHashSet<>();
        Set<String> directWarehouses = new LinkedHashSet<>();
        Set<String> directShipments = new LinkedHashSet<>();
        Set<String> directQa = new LinkedHashSet<>();

        for (InvestigationEvidenceResponse item : primaryEvidence.values()) {
            String m = item.getMachineReference();
            String p = item.getProductReference();
            String s = item.getSupplierReference();
            String zone = extractStoredField(item.getNormalizedPayload(), "warehouse_zone", "warehouseZone", "warehouse_id", "warehouseId", "zone", "location_id", "facility");
            String shipId = extractStoredField(item.getNormalizedPayload(), "shipment_id", "shipmentId", "shipment_number", "shipment", "dispatch_id", "tracking_number");
            String qaId = extractStoredField(item.getNormalizedPayload(), "sample_id", "lims_sample_id", "test_id", "qa_id", "lims_id", "inspection_id");

            if (m != null && !m.isBlank()) directMachines.add(m.trim());
            if (p != null && !p.isBlank()) directProducts.add(p.trim());
            if (s != null && !s.isBlank()) directSuppliers.add(s.trim());

            if (zone != null && !zone.isBlank()) {
                directWarehouses.add(zone.trim());
            } else if ("WAREHOUSE".equalsIgnoreCase(item.getSourceType())) {
                String fallbackZone = item.getSourceRecordId() != null ? item.getSourceRecordId() : item.getStableId();
                directWarehouses.add(fallbackZone);
            }

            if (shipId != null && !shipId.isBlank()) {
                directShipments.add(shipId.trim());
            } else if ("SHIPMENT".equalsIgnoreCase(item.getSourceType())) {
                String fallbackShip = item.getSourceRecordId() != null ? item.getSourceRecordId() : item.getStableId();
                directShipments.add(fallbackShip);
            }

            if (qaId != null && !qaId.isBlank()) {
                directQa.add(qaId.trim());
            } else if ("LIMS".equalsIgnoreCase(item.getSourceType()) || "QA".equalsIgnoreCase(item.getSourceType())) {
                String fallbackQa = item.getSourceRecordId() != null ? item.getSourceRecordId() : item.getStableId();
                directQa.add(fallbackQa);
            }
        }
        directMachines.addAll(directMachineIds);
        directSuppliers.addAll(directSupplierIds);

        // 2. Build hierarchical nodes and edges
        Map<String, BatchConnectionMapResponse.ConnectionNode> nodes = new LinkedHashMap<>();
        Map<String, BatchConnectionMapResponse.ConnectionEdge> edges = new LinkedHashMap<>();

        // Root Batch is ALWAYS root (Depth 0)
        String batchKey = nodeKey("Batch", batch);
        nodes.put(batchKey, BatchConnectionMapResponse.ConnectionNode.builder()
                .key(batchKey).type("Batch").stableId(batch).label("Batch " + batch).direct(true).build());

        // Primary: Products (only direct products, no inferred product->machine)
        for (String prod : directProducts) {
            String prodKey = nodeKey("Product", prod);
            nodes.putIfAbsent(prodKey, BatchConnectionMapResponse.ConnectionNode.builder()
                    .key(prodKey).type("Product").stableId(prod).label("Product " + prod).direct(true).build());
            edges.putIfAbsent(batchKey + "->" + prodKey, BatchConnectionMapResponse.ConnectionEdge.builder()
                    .from(batchKey).to(prodKey).type("produces").direct(true).build());
        }

        // Primary: Direct Machines (not attached to a product node)
        for (String mach : directMachines) {
            String machKey = nodeKey("Machine", mach);
            if (!nodes.containsKey(machKey)) {
                nodes.put(machKey, BatchConnectionMapResponse.ConnectionNode.builder()
                        .key(machKey).type("Machine").stableId(mach).label("Machine " + mach).direct(true).build());
                edges.putIfAbsent(batchKey + "->" + machKey, BatchConnectionMapResponse.ConnectionEdge.builder()
                        .from(batchKey).to(machKey).type("processed on").direct(true).build());
            }
        }

        // Primary: Suppliers (supplied by)
        for (String sup : directSuppliers) {
            String supKey = nodeKey("Supplier", sup);
            nodes.putIfAbsent(supKey, BatchConnectionMapResponse.ConnectionNode.builder()
                    .key(supKey).type("Supplier").stableId(sup).label("Supplier " + sup).direct(true).build());
            edges.putIfAbsent(batchKey + "->" + supKey, BatchConnectionMapResponse.ConnectionEdge.builder()
                    .from(batchKey).to(supKey).type("supplied by").direct(true).build());
        }

        // Primary: Warehouse Zones (stored in)
        for (String wh : directWarehouses) {
            String whKey = nodeKey("Warehouse", wh);
            nodes.putIfAbsent(whKey, BatchConnectionMapResponse.ConnectionNode.builder()
                    .key(whKey).type("Warehouse").stableId(wh).label(wh.startsWith("Zone") ? wh : "Warehouse " + wh).direct(true).build());
            edges.putIfAbsent(batchKey + "->" + whKey, BatchConnectionMapResponse.ConnectionEdge.builder()
                    .from(batchKey).to(whKey).type("stored in").direct(true).build());
        }

        // Primary: Shipments (shipped in)
        for (String ship : directShipments) {
            String shipKey = nodeKey("Shipment", ship);
            nodes.putIfAbsent(shipKey, BatchConnectionMapResponse.ConnectionNode.builder()
                    .key(shipKey).type("Shipment").stableId(ship).label("Shipment " + ship).direct(true).build());
            edges.putIfAbsent(batchKey + "->" + shipKey, BatchConnectionMapResponse.ConnectionEdge.builder()
                    .from(batchKey).to(shipKey).type("shipped in").direct(true).build());
        }

        // Primary: QA / LIMS (tested by)
        for (String qa : directQa) {
            String qaKey = nodeKey("QA", qa);
            nodes.putIfAbsent(qaKey, BatchConnectionMapResponse.ConnectionNode.builder()
                    .key(qaKey).type("QA").stableId(qa).label("QA " + qa).direct(true).build());
            edges.putIfAbsent(batchKey + "->" + qaKey, BatchConnectionMapResponse.ConnectionEdge.builder()
                    .from(batchKey).to(qaKey).type("tested by").direct(true).build());
        }

        // 3. Connect Evidence nodes to their discovered parent entities (Leaf Level) - PRIMARY only
        for (InvestigationEvidenceResponse item : primaryEvidence.values()) {
            String evidenceKey = nodeKey(item.getSourceType() == null ? "Evidence" : item.getSourceType(), item.getStableId());
            boolean isBatchDirect = batch.equalsIgnoreCase(item.getBatchReference());
            nodes.putIfAbsent(evidenceKey, BatchConnectionMapResponse.ConnectionNode.builder()
                    .key(evidenceKey).type(item.getSourceType() == null ? "Evidence" : item.getSourceType())
                    .stableId(item.getStableId()).label(item.getTitle() != null && !item.getTitle().isBlank() ? item.getTitle() : item.getStableId())
                    .sourceType(item.getSourceType()).evidence(true).direct(isBatchDirect).build());

            String m = item.getMachineReference();
            String p = item.getProductReference();
            String s = item.getSupplierReference();
            String zone = extractStoredField(item.getNormalizedPayload(), "warehouse_zone", "warehouseZone", "warehouse_id", "warehouseId", "zone", "location_id", "facility");
            String shipId = extractStoredField(item.getNormalizedPayload(), "shipment_id", "shipmentId", "shipment_number", "shipment", "dispatch_id", "tracking_number");
            String qaId = extractStoredField(item.getNormalizedPayload(), "sample_id", "lims_sample_id", "test_id", "qa_id", "lims_id", "inspection_id");

            if (m != null && !m.isBlank()) {
                String machKey = nodeKey("Machine", m.trim());
                String edgeType = "CMMS".equalsIgnoreCase(item.getSourceType()) ? "maintenance" : "procedure";
                edges.putIfAbsent(machKey + "->" + evidenceKey, BatchConnectionMapResponse.ConnectionEdge.builder()
                        .from(machKey).to(evidenceKey).type(edgeType).direct(false).build());
            } else if ("WAREHOUSE".equalsIgnoreCase(item.getSourceType()) || (zone != null && !zone.isBlank())) {
                String z = (zone != null && !zone.isBlank()) ? zone.trim() : (item.getSourceRecordId() != null ? item.getSourceRecordId() : item.getStableId());
                String whKey = nodeKey("Warehouse", z);
                edges.putIfAbsent(whKey + "->" + evidenceKey, BatchConnectionMapResponse.ConnectionEdge.builder()
                        .from(whKey).to(evidenceKey).type("log record").direct(true).build());
            } else if ("SHIPMENT".equalsIgnoreCase(item.getSourceType()) || (shipId != null && !shipId.isBlank())) {
                String sid = (shipId != null && !shipId.isBlank()) ? shipId.trim() : (item.getSourceRecordId() != null ? item.getSourceRecordId() : item.getStableId());
                String shipKey = nodeKey("Shipment", sid);
                edges.putIfAbsent(shipKey + "->" + evidenceKey, BatchConnectionMapResponse.ConnectionEdge.builder()
                        .from(shipKey).to(evidenceKey).type("distribution").direct(true).build());
                // If customer exists on shipment payload, connect Customer under Shipment
                String cust = extractStoredField(item.getNormalizedPayload(), "customer_id", "customerId", "customer", "customer_name");
                if (cust != null && !cust.isBlank()) {
                    String custKey = nodeKey("Customer", cust.trim());
                    nodes.putIfAbsent(custKey, BatchConnectionMapResponse.ConnectionNode.builder()
                            .key(custKey).type("Customer").stableId(cust.trim()).label("Customer " + cust.trim()).direct(false).build());
                    edges.putIfAbsent(shipKey + "->" + custKey, BatchConnectionMapResponse.ConnectionEdge.builder()
                            .from(shipKey).to(custKey).type("customer").direct(false).build());
                }
            } else if ("ERP".equalsIgnoreCase(item.getSourceType()) || (s != null && !s.isBlank())) {
                String sid = (s != null && !s.isBlank()) ? s.trim() : (item.getSourceRecordId() != null ? item.getSourceRecordId() : item.getStableId());
                String supKey = nodeKey("Supplier", sid);
                edges.putIfAbsent(supKey + "->" + evidenceKey, BatchConnectionMapResponse.ConnectionEdge.builder()
                        .from(supKey).to(evidenceKey).type("erp record").direct(true).build());
            } else if ("CRM".equalsIgnoreCase(item.getSourceType()) || (p != null && !p.isBlank())) {
                String pid = (p != null && !p.isBlank()) ? p.trim() : directProducts.isEmpty() ? null : directProducts.iterator().next();
                if (pid != null) {
                    String prodKey = nodeKey("Product", pid);
                    edges.putIfAbsent(prodKey + "->" + evidenceKey, BatchConnectionMapResponse.ConnectionEdge.builder()
                            .from(prodKey).to(evidenceKey).type("complaint").direct(isBatchDirect).build());
                }
            } else if ("LIMS".equalsIgnoreCase(item.getSourceType()) || "QA".equalsIgnoreCase(item.getSourceType()) || (qaId != null && !qaId.isBlank())) {
                String qid = (qaId != null && !qaId.isBlank()) ? qaId.trim() : (item.getSourceRecordId() != null ? item.getSourceRecordId() : item.getStableId());
                String qaKey = nodeKey("QA", qid);
                edges.putIfAbsent(qaKey + "->" + evidenceKey, BatchConnectionMapResponse.ConnectionEdge.builder()
                        .from(qaKey).to(evidenceKey).type("lab test").direct(true).build());
            } else {
                edges.putIfAbsent(batchKey + "->" + evidenceKey, BatchConnectionMapResponse.ConnectionEdge.builder()
                        .from(batchKey).to(evidenceKey).type("evidence").direct(isBatchDirect).build());
            }
        }

        // Also integrate any custom paths from match explanations (stripping any Incident: prefix) - PRIMARY only
        for (InvestigationEvidenceResponse item : primaryEvidence.values()) {
            List<EvidenceMatchExplanation> explanations = item.getMatchExplanations() == null ? List.of() : item.getMatchExplanations();
            for (EvidenceMatchExplanation explanation : explanations) {
                String reason = explanation.getReason();
                if ("PRODUCT_MACHINE_MATCH".equals(reason)) continue;
                List<String> rawPath = explanation.getConnectionPath() == null ? List.of() : explanation.getConnectionPath();
                if (rawPath.isEmpty()) continue;
                List<String> cleanPath = new ArrayList<>();
                for (String token : rawPath) {
                    if (token != null && !token.startsWith("Incident:")) cleanPath.add(token);
                }
                if (cleanPath.isEmpty()) continue;
                if (!cleanPath.get(0).startsWith("Batch:")) cleanPath.add(0, "Batch:" + batch);
                boolean pathValid = true;
                for (String token : cleanPath) {
                    if (token.startsWith("Machine:")) {
                        String mid = token.substring("Machine:".length());
                        if (!directMachineIds.contains(mid) && !batch.equalsIgnoreCase(item.getBatchReference())) {
                            if (!directMachines.contains(mid)) { pathValid = false; break; }
                        }
                    }
                }
                if (!pathValid) continue;
                addPath(cleanPath, item, nodes, edges);
            }
        }

        // Enforce BFS Graph Reachability: Keep ONLY nodes and edges that have a valid path starting from Batch:<batch>
        Set<String> reachableNodes = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(batchKey);
        reachableNodes.add(batchKey);

        Map<String, List<String>> adj = new LinkedHashMap<>();
        for (var edge : edges.values()) {
            adj.computeIfAbsent(edge.getFrom(), k -> new ArrayList<>()).add(edge.getTo());
        }

        while (!queue.isEmpty()) {
            String curr = queue.poll();
            List<String> neighbors = adj.getOrDefault(curr, List.of());
            for (String neighbor : neighbors) {
                if (reachableNodes.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }

        nodes.keySet().retainAll(reachableNodes);
        edges.entrySet().removeIf(e -> !reachableNodes.contains(e.getValue().getFrom()) || !reachableNodes.contains(e.getValue().getTo()));

        // Extract structured investigation signals - PRIMARY only
        List<com.taceiq.dto.InvestigationSignalDto> signals = extractInvestigationSignals(primaryEvidence.values(), batch);

        // Map signals to node keys for signal badge indicators & node priority
        Map<String, List<com.taceiq.dto.InvestigationSignalDto>> nodeSignals = new LinkedHashMap<>();
        for (var sig : signals) {
            if (sig.getEntityId() != null && !sig.getEntityId().isBlank()) {
                String nodeKey = nodeKey(sig.getEntityType() != null ? sig.getEntityType() : "Evidence", sig.getEntityId());
                nodeSignals.computeIfAbsent(nodeKey, k -> new ArrayList<>()).add(sig);
            }
            if (sig.getSourceEvidenceId() != null) {
                String evKey = nodeKey(sig.getSourceSystem() != null ? sig.getSourceSystem() : "Evidence", sig.getSourceEvidenceId());
                nodeSignals.computeIfAbsent(evKey, k -> new ArrayList<>()).add(sig);
            }
        }

        // Update ConnectionNode attributes (priority, signalCount, signalTypes)
        List<BatchConnectionMapResponse.ConnectionNode> enrichedNodes = new ArrayList<>();
        for (var node : nodes.values()) {
            List<com.taceiq.dto.InvestigationSignalDto> sigsForNode = nodeSignals.getOrDefault(node.getKey(), List.of());
            int sigCount = sigsForNode.size();
            List<String> sigTypes = sigsForNode.stream().map(com.taceiq.dto.InvestigationSignalDto::getSignalType).distinct().toList();
            String priority = sigTypes.contains("CRITICAL") ? "HIGH" : sigTypes.contains("WARNING") ? "MEDIUM" : "LOW";
            enrichedNodes.add(BatchConnectionMapResponse.ConnectionNode.builder()
                    .key(node.getKey())
                    .type(node.getType())
                    .stableId(node.getStableId())
                    .label(node.getLabel())
                    .sourceType(node.getSourceType())
                    .evidence(node.isEvidence())
                    .direct(node.isDirect())
                    .priority(priority)
                    .signalCount(sigCount)
                    .signalTypes(sigTypes)
                    .build());
        }

        // Build prioritized investigation paths - PRIMARY only
        List<com.taceiq.dto.InvestigationPathDto> paths = buildInvestigationPaths(primaryEvidence.values(), signals, batch);

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("totalConnections", edges.size());
        counts.put("evidenceRecords", primaryEvidence.size());
        counts.put("products", countType(nodes, "Product"));
        counts.put("machines", countType(nodes, "Machine"));
        counts.put("suppliers", countType(nodes, "Supplier"));
        counts.put("shipments", countType(nodes, "Shipment"));
        counts.put("warehouseRecords", countType(nodes, "Warehouse"));
        counts.put("qaLimsRecords", countType(nodes, "LIMS") + countType(nodes, "QA"));

        // --- CROSS-BATCH CONTEXT ---
        Map<String, BatchConnectionMapResponse.ConnectionNode> crossNodes = new LinkedHashMap<>();
        Map<String, BatchConnectionMapResponse.ConnectionEdge> crossEdges = new LinkedHashMap<>();
        for (InvestigationEvidenceResponse item : crossBatchEvidenceMap.values()) {
            String evidenceKey = nodeKey(item.getSourceType() == null ? "Evidence" : item.getSourceType(), item.getStableId());
            crossNodes.putIfAbsent(evidenceKey, BatchConnectionMapResponse.ConnectionNode.builder()
                    .key(evidenceKey).type(item.getSourceType() == null ? "Evidence" : item.getSourceType())
                    .stableId(item.getStableId()).label(item.getTitle() != null && !item.getTitle().isBlank() ? item.getTitle() : item.getStableId())
                    .sourceType(item.getSourceType()).evidence(true).direct(false).build());
            if (item.getMachineReference() != null && !item.getMachineReference().isBlank()) {
                String machKey = nodeKey("Machine", item.getMachineReference().trim());
                crossNodes.putIfAbsent(machKey, BatchConnectionMapResponse.ConnectionNode.builder()
                        .key(machKey).type("Machine").stableId(item.getMachineReference().trim()).label("Machine " + item.getMachineReference().trim()).direct(false).build());
                crossEdges.putIfAbsent(machKey + "->" + evidenceKey, BatchConnectionMapResponse.ConnectionEdge.builder()
                        .from(machKey).to(evidenceKey).type("maintenance").direct(false).build());
            }
        }
        List<BatchConnectionMapResponse.RelatedBatchInfo> relatedBatches = new ArrayList<>();
        Map<String, BatchConnectionMapResponse.RelatedBatchInfo> relatedMap = new LinkedHashMap<>();
        if (sourceRecordRepository != null && !directProductIds.isEmpty()) {
            for (String prod : directProductIds) {
                try {
                    List<com.taceiq.entity.IngestedSourceRecord> prodRecords = sourceRecordRepository.findByOrganisationOrgIdAndProductReference(orgId, prod);
                    for (com.taceiq.entity.IngestedSourceRecord r : prodRecords) {
                        String otherBatch = r.getBatchReference();
                        if (otherBatch == null || otherBatch.isBlank() || batch.equalsIgnoreCase(otherBatch.trim())) continue;
                        String key = otherBatch.trim();
                        if (!relatedMap.containsKey(key)) {
                            long cnt = sourceRecordRepository.countByOrganisationOrgIdAndBatchReference(orgId, key);
                            relatedMap.put(key, BatchConnectionMapResponse.RelatedBatchInfo.builder()
                                    .batchReference(key)
                                    .sharedAttributeType("Product")
                                    .sharedAttributeValue(prod)
                                    .reason("SHARED_PRODUCT")
                                    .evidenceCount((int) cnt)
                                    .evidenceIds(List.of())
                                    .build());
                            String bKey = nodeKey("Batch", key);
                            crossNodes.putIfAbsent(bKey, BatchConnectionMapResponse.ConnectionNode.builder()
                                    .key(bKey).type("Batch").stableId(key).label("Batch " + key).direct(false).build());
                            String prodKey = nodeKey("Product", prod);
                            crossNodes.putIfAbsent(prodKey, BatchConnectionMapResponse.ConnectionNode.builder()
                                    .key(prodKey).type("Product").stableId(prod).label("Product " + prod).direct(false).build());
                            crossEdges.putIfAbsent(bKey + "->" + prodKey, BatchConnectionMapResponse.ConnectionEdge.builder()
                                    .from(bKey).to(prodKey).type("produces").direct(false).build());
                            if (r.getMachineReference() != null && !r.getMachineReference().isBlank()) {
                                String mKey = nodeKey("Machine", r.getMachineReference().trim());
                                crossNodes.putIfAbsent(mKey, BatchConnectionMapResponse.ConnectionNode.builder()
                                        .key(mKey).type("Machine").stableId(r.getMachineReference().trim()).label("Machine " + r.getMachineReference().trim()).direct(false).build());
                                crossEdges.putIfAbsent(prodKey + "->" + mKey, BatchConnectionMapResponse.ConnectionEdge.builder()
                                        .from(prodKey).to(mKey).type("processed on").direct(false).build());
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        if (relatedMap.isEmpty()) {
            Map<String, List<String>> batchToEvidence = new LinkedHashMap<>();
            for (InvestigationEvidenceResponse item : crossBatchEvidenceMap.values()) {
                String b = item.getBatchReference();
                if (b != null && !b.isBlank() && !batch.equalsIgnoreCase(b.trim())) {
                    batchToEvidence.computeIfAbsent(b.trim(), k -> new ArrayList<>()).add(item.getStableId());
                }
            }
            for (Map.Entry<String, List<String>> e : batchToEvidence.entrySet()) {
                relatedMap.put(e.getKey(), BatchConnectionMapResponse.RelatedBatchInfo.builder()
                        .batchReference(e.getKey())
                        .sharedAttributeType("Product")
                        .sharedAttributeValue(directProductIds.isEmpty() ? "" : directProductIds.iterator().next())
                        .reason("SHARED_PRODUCT")
                        .evidenceCount(e.getValue().size())
                        .evidenceIds(e.getValue())
                        .build());
            }
        }
        relatedBatches.addAll(relatedMap.values());
        Map<String, Integer> crossCounts = new LinkedHashMap<>();
        crossCounts.put("evidenceRecords", crossBatchEvidenceMap.size());
        crossCounts.put("relatedBatches", relatedBatches.size());
        crossCounts.put("totalConnections", crossEdges.size());

        return BatchConnectionMapResponse.builder()
                .incidentId(investigationId)
                .batchReference(batch)
                .counts(counts)
                .nodes(enrichedNodes)
                .relationships(new ArrayList<>(edges.values()))
                .evidence(new ArrayList<>(primaryEvidence.values()))
                .signals(signals)
                .paths(paths)
                .crossBatchCounts(crossCounts)
                .crossBatchNodes(new ArrayList<>(crossNodes.values()))
                .crossBatchRelationships(new ArrayList<>(crossEdges.values()))
                .crossBatchEvidence(new ArrayList<>(crossBatchEvidenceMap.values()))
                .relatedBatches(relatedBatches)
                .build();
    }

    private boolean isPrimaryEvidence(InvestigationEvidenceResponse item, String batch, Set<String> directMachineIds, Set<String> directSupplierIds) {
        if (item.getBatchReference() != null && batch.equalsIgnoreCase(item.getBatchReference().trim())) return true;
        if (item.getMachineReference() != null && !item.getMachineReference().isBlank() && directMachineIds.contains(item.getMachineReference().trim())) return true;
        if (item.getSupplierReference() != null && !item.getSupplierReference().isBlank() && directSupplierIds.contains(item.getSupplierReference().trim())) return true;
        if (item.getMatchExplanations() != null) {
            for (EvidenceMatchExplanation exp : item.getMatchExplanations()) {
                String r = exp.getReason();
                if ("DIRECT_BATCH_MATCH".equals(r) || "MACHINE_MATCH_FROM_BATCH".equals(r) || "SUPPLIER_MATCH_FROM_BATCH".equals(r) || "PRODUCT_MATCH_FROM_BATCH".equals(r) || "SHIPMENT_MATCH_FROM_BATCH".equals(r) || "WAREHOUSE_MATCH_FROM_BATCH".equals(r) || "QA_MATCH_FROM_BATCH".equals(r)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<com.taceiq.dto.InvestigationSignalDto> extractInvestigationSignals(Collection<InvestigationEvidenceResponse> evidenceItems, String batch) {
        List<com.taceiq.dto.InvestigationSignalDto> signals = new ArrayList<>();
        Set<String> seenSignalKeys = new HashSet<>();
        int sigIdCounter = 1;

        for (InvestigationEvidenceResponse item : evidenceItems) {
            String payload = item.getNormalizedPayload();
            String status = item.getStatus();
            String sourceType = item.getSourceType() != null ? item.getSourceType() : "Evidence";
            List<String> connPath = (item.getMatchExplanations() != null && !item.getMatchExplanations().isEmpty() && item.getMatchExplanations().get(0).getConnectionPath() != null)
                    ? item.getMatchExplanations().get(0).getConnectionPath()
                    : List.of("Batch:" + batch, sourceType + ":" + item.getStableId());

            String entityType = sourceType;
            String entityId = item.getStableId();
            if (item.getMachineReference() != null && !item.getMachineReference().isBlank()) {
                entityType = "Machine"; entityId = item.getMachineReference().trim();
            } else if (item.getSupplierReference() != null && !item.getSupplierReference().isBlank()) {
                entityType = "Supplier"; entityId = item.getSupplierReference().trim();
            } else if (item.getProductReference() != null && !item.getProductReference().isBlank()) {
                entityType = "Product"; entityId = item.getProductReference().trim();
            }

            boolean signalExtracted = false;

            // 1. Structured Check: QA / Test Failure (status or result field = FAILED / OUT_OF_SPEC)
            String rawResult = extractStoredField(payload, "result", "test_result", "qa_status", "status");
            if (rawResult == null && ("LIMS".equalsIgnoreCase(sourceType) || "QA".equalsIgnoreCase(sourceType))) {
                rawResult = item.getStatus();
            }
            if (rawResult != null && (rawResult.equalsIgnoreCase("FAILED") || rawResult.equalsIgnoreCase("FAIL") || rawResult.equalsIgnoreCase("OUT_OF_SPEC") || rawResult.equalsIgnoreCase("REJECTED"))) {
                String exactField = extractStoredField(payload, "result") != null ? "result" : "status";
                String sigKey = item.getStableId() + "|CRITICAL|" + exactField + "|" + rawResult;
                if (seenSignalKeys.add(sigKey)) {
                    signals.add(com.taceiq.dto.InvestigationSignalDto.builder()
                            .id("sig-" + (sigIdCounter++))
                            .sourceEvidenceId(item.getStableId())
                            .sourceSystem(sourceType)
                            .exactField(exactField)
                            .actualValue(rawResult)
                            .expectedValue("PASS")
                            .signalType("CRITICAL")
                            .deterministicReason("Source record contains explicit failed status (" + rawResult + ") in quality test")
                            .entityType(entityType)
                            .entityId(entityId)
                            .connectionPath(connPath)
                            .discoveredAt(Instant.now())
                            .build());
                    signalExtracted = true;
                }
            }

            // 2. Structured Check: Maintenance Risk (status or maintenanceStatus = OVERDUE)
            String maintField = extractStoredFieldName(payload, "maintenanceStatus", "maintenance_status");
            String maintStatus = maintField != null ? extractStoredField(payload, maintField) : null;
            if (maintStatus == null && ("CMMS".equalsIgnoreCase(sourceType) || "MAINTENANCE".equalsIgnoreCase(sourceType))) {
                maintStatus = extractStoredField(payload, "status");
                if (maintStatus == null) maintStatus = item.getStatus();
            }
            if (!signalExtracted && maintStatus != null && (maintStatus.equalsIgnoreCase("OVERDUE") || maintStatus.equalsIgnoreCase("DELAYED"))) {
                String exactField = maintField != null ? maintField : "status";
                String sigKey = item.getStableId() + "|WARNING|" + exactField + "|" + maintStatus;
                if (seenSignalKeys.add(sigKey)) {
                    signals.add(com.taceiq.dto.InvestigationSignalDto.builder()
                            .id("sig-" + (sigIdCounter++))
                            .sourceEvidenceId(item.getStableId())
                            .sourceSystem(sourceType)
                            .exactField(exactField)
                            .actualValue(maintStatus)
                            .expectedValue("COMPLETED")
                            .signalType("WARNING")
                            .deterministicReason("Maintenance record is marked overdue in source CMMS data for " + entityType + " " + entityId)
                            .entityType(entityType)
                            .entityId(entityId)
                            .connectionPath(connPath)
                            .discoveredAt(Instant.now())
                            .build());
                    signalExtracted = true;
                }
            }

            // 3. Structured Check: Shipment Recall (shipmentStatus = RECALLED / HOLD)
            String shipField = extractStoredFieldName(payload, "shipmentStatus", "shipment_status");
            String shipStatus = shipField != null ? extractStoredField(payload, shipField) : null;
            if (shipStatus == null && ("SHIPMENT".equalsIgnoreCase(sourceType) || "LOGISTICS".equalsIgnoreCase(sourceType))) {
                shipStatus = extractStoredField(payload, "status");
                if (shipStatus == null) shipStatus = item.getStatus();
            }
            if (!signalExtracted && shipStatus != null && (shipStatus.equalsIgnoreCase("RECALLED") || shipStatus.equalsIgnoreCase("HOLD") || shipStatus.equalsIgnoreCase("DAMAGED"))) {
                String exactField = shipField != null ? shipField : "status";
                String sigKey = item.getStableId() + "|CRITICAL|" + exactField + "|" + shipStatus;
                if (seenSignalKeys.add(sigKey)) {
                    signals.add(com.taceiq.dto.InvestigationSignalDto.builder()
                            .id("sig-" + (sigIdCounter++))
                            .sourceEvidenceId(item.getStableId())
                            .sourceSystem(sourceType)
                            .exactField(exactField)
                            .actualValue(shipStatus)
                            .expectedValue("DELIVERED")
                            .signalType("CRITICAL")
                            .deterministicReason("Shipment distribution record marked " + shipStatus + " in source logistics data")
                            .entityType(entityType)
                            .entityId(entityId)
                            .connectionPath(connPath)
                            .discoveredAt(Instant.now())
                            .build());
                    signalExtracted = true;
                }
            }

            // 4. Structured Check: Out-of-Spec Numeric Measurement
            String measValStr = extractStoredField(payload, "measuredValue", "measured_value", "value", "reading");
            String minLimStr = extractStoredField(payload, "minLimit", "min_limit", "lowerSpecLimit", "lower_spec_limit");
            String maxLimStr = extractStoredField(payload, "maxLimit", "max_limit", "upperSpecLimit", "upper_spec_limit");

            if (!signalExtracted && measValStr != null) {
                try {
                    double val = Double.parseDouble(measValStr.replaceAll("[^0-9.]", ""));
                    Double minLim = minLimStr != null ? Double.parseDouble(minLimStr.replaceAll("[^0-9.]", "")) : null;
                    Double maxLim = maxLimStr != null ? Double.parseDouble(maxLimStr.replaceAll("[^0-9.]", "")) : null;

                    if ((minLim != null && val < minLim) || (maxLim != null && val > maxLim)) {
                        String expected = minLim != null && maxLim != null ? minLimStr + " – " + maxLimStr : minLim != null ? "≥ " + minLimStr : "≤ " + maxLimStr;
                        String sigKey = item.getStableId() + "|CRITICAL|measuredValue|" + measValStr;
                        if (seenSignalKeys.add(sigKey)) {
                            signals.add(com.taceiq.dto.InvestigationSignalDto.builder()
                                    .id("sig-" + (sigIdCounter++))
                                    .sourceEvidenceId(item.getStableId())
                                    .sourceSystem(sourceType)
                                    .exactField("measuredValue")
                                    .actualValue(measValStr)
                                    .expectedValue(expected)
                                    .signalType("CRITICAL")
                                    .deterministicReason("Measured value " + measValStr + " is outside specification limits (" + expected + ")")
                                    .entityType(entityType)
                                    .entityId(entityId)
                                    .connectionPath(connPath)
                                    .discoveredAt(Instant.now())
                                    .build());
                            signalExtracted = true;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return signals;
    }

    private List<com.taceiq.dto.InvestigationPathDto> buildInvestigationPaths(Collection<InvestigationEvidenceResponse> evidenceItems,
                                                                               List<com.taceiq.dto.InvestigationSignalDto> signals,
                                                                               String batch) {
        List<com.taceiq.dto.InvestigationPathDto> paths = new ArrayList<>();
        Map<String, List<com.taceiq.dto.InvestigationSignalDto>> signalsByEv = new LinkedHashMap<>();
        for (var sig : signals) {
            signalsByEv.computeIfAbsent(sig.getSourceEvidenceId(), k -> new ArrayList<>()).add(sig);
        }

        Set<String> seenPathKeys = new HashSet<>();
        int pathIdCounter = 1;
        for (InvestigationEvidenceResponse item : evidenceItems) {
            List<com.taceiq.dto.InvestigationSignalDto> evSignals = signalsByEv.getOrDefault(item.getStableId(), List.of());
            List<EvidenceMatchExplanation> explanations = item.getMatchExplanations() != null ? item.getMatchExplanations() : List.of();

            List<List<String>> candidatePaths = new ArrayList<>();
            if (explanations.isEmpty()) {
                List<String> cleanPath = new ArrayList<>();
                cleanPath.add("Batch:" + batch);
                if (item.getProductReference() != null && !item.getProductReference().isBlank()) {
                    cleanPath.add("Product:" + item.getProductReference());
                }
                if (item.getMachineReference() != null && !item.getMachineReference().isBlank()) {
                    cleanPath.add("Machine:" + item.getMachineReference());
                } else if (item.getSupplierReference() != null && !item.getSupplierReference().isBlank()) {
                    cleanPath.add("Supplier:" + item.getSupplierReference());
                }
                cleanPath.add((item.getSourceType() != null ? item.getSourceType() : "Evidence") + ":" + item.getStableId());
                candidatePaths.add(cleanPath);
            } else {
                for (EvidenceMatchExplanation exp : explanations) {
                    List<String> rawPath = exp.getConnectionPath() != null ? exp.getConnectionPath() : List.of();
                    List<String> cleanPath = new ArrayList<>();
                    for (String t : rawPath) {
                        if (t != null && !t.startsWith("Incident:")) {
                            cleanPath.add(t.replaceAll("\\s+Evidence:", ":"));
                        }
                    }
                    if (cleanPath.isEmpty()) continue;
                    if (!cleanPath.get(0).startsWith("Batch:")) cleanPath.add(0, "Batch:" + batch);
                    candidatePaths.add(cleanPath);
                }
            }

            // Path Deduplication:
            // Sort candidate paths by length (prefer direct/shorter paths)
            candidatePaths.sort(Comparator.comparingInt(List::size));

            Set<String> targetEntitiesInItemPaths = new HashSet<>();
            for (List<String> cleanPath : candidatePaths) {
                String pathKey = String.join("->", cleanPath);
                if (seenPathKeys.contains(pathKey)) continue;

                // Check if this path is a redundant indirect path reaching a target entity that already has a direct path in this item
                String directEntitySig = cleanPath.size() > 2 ? cleanPath.get(cleanPath.size() - 2) : cleanPath.get(cleanPath.size() - 1);
                if (cleanPath.size() > 3 && targetEntitiesInItemPaths.contains(directEntitySig)) {
                    // Suppress redundant longer path for same target entity
                    continue;
                }

                seenPathKeys.add(pathKey);
                targetEntitiesInItemPaths.add(directEntitySig);

                boolean hasCritical = evSignals.stream().anyMatch(s -> "CRITICAL".equalsIgnoreCase(s.getSignalType()));
                boolean hasWarning = evSignals.stream().anyMatch(s -> "WARNING".equalsIgnoreCase(s.getSignalType()));
                String priority = hasCritical ? "HIGH" : hasWarning ? "MEDIUM" : "LOW";

                String targetEntityId = item.getMachineReference() != null ? item.getMachineReference() : item.getStableId();
                String title = (item.getSourceType() != null ? item.getSourceType() : "Evidence") + " Path (" + targetEntityId + ")";
                String reason = !evSignals.isEmpty()
                        ? evSignals.get(0).getDeterministicReason()
                        : "Source-supported connection path for " + item.getStableId() + " connected to batch " + batch;

                paths.add(com.taceiq.dto.InvestigationPathDto.builder()
                        .id("path-" + (pathIdCounter++))
                        .title(title)
                        .priority(priority)
                        .reason(reason)
                        .path(cleanPath)
                        .targetEvidenceId(item.getStableId())
                        .targetEntityId(targetEntityId)
                        .signalCount(evSignals.size())
                        .build());
            }
        }

        // Sort paths by priority: HIGH first, then MEDIUM, then LOW
        paths.sort((p1, p2) -> {
            int val1 = "HIGH".equalsIgnoreCase(p1.getPriority()) ? 0 : "MEDIUM".equalsIgnoreCase(p1.getPriority()) ? 1 : 2;
            int val2 = "HIGH".equalsIgnoreCase(p2.getPriority()) ? 0 : "MEDIUM".equalsIgnoreCase(p2.getPriority()) ? 1 : 2;
            return Integer.compare(val1, val2);
        });

        return paths;
    }

    private void addPath(List<String> path, InvestigationEvidenceResponse item,
                         Map<String, BatchConnectionMapResponse.ConnectionNode> nodes,
                         Map<String, BatchConnectionMapResponse.ConnectionEdge> edges) {
        for (int i = 0; i < path.size(); i++) {
            String token = path.get(i);
            int separator = token.indexOf(':');
            String type = separator > 0 ? token.substring(0, separator) : "Evidence";
            String stableId = separator > 0 ? token.substring(separator + 1) : token;
            String key = nodeKey(type, stableId);
            boolean evidence = i == path.size() - 1;
            nodes.putIfAbsent(key, BatchConnectionMapResponse.ConnectionNode.builder()
                    .key(key).type(type).stableId(stableId).label(evidence ? (item.getTitle() != null ? item.getTitle() : stableId) : stableId)
                    .sourceType(evidence ? item.getSourceType() : null).evidence(evidence).direct(path.size() <= 3).build());
            if (i > 0) {
                String previous = nodeKey(path.get(i - 1).contains(":") ? path.get(i - 1).substring(0, path.get(i - 1).indexOf(':')) : "Evidence",
                        path.get(i - 1).contains(":") ? path.get(i - 1).substring(path.get(i - 1).indexOf(':') + 1) : path.get(i - 1));
                String edgeKey = previous + "->" + key;
                edges.putIfAbsent(edgeKey, BatchConnectionMapResponse.ConnectionEdge.builder()
                        .from(previous).to(key).type("CONNECTED").direct(path.size() <= 3).build());
            }
        }
    }

    private static String nodeKey(String type, String id) { return type + ":" + id; }

    private static int countType(Map<String, BatchConnectionMapResponse.ConnectionNode> nodes, String type) {
        return (int) nodes.values().stream()
                .filter(node -> !node.isEvidence())
                .filter(node -> type.equalsIgnoreCase(node.getType()))
                .count();
    }

    private static String extractStoredFieldName(String payload, String... fields) {
        if (payload == null || payload.isBlank()) return null;
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(payload);
            for (String field : fields) {
                if (node.has(field) && !node.get(field).isNull()) {
                    return field;
                }
                var it = node.fieldNames();
                while (it.hasNext()) {
                    String key = it.next();
                    if (key.equalsIgnoreCase(field) && !node.get(key).isNull()) {
                        return key;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
