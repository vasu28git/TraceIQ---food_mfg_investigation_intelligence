package com.taceiq.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taceiq.dto.FindingEvidenceTraceabilityResponse;
import com.taceiq.entity.*;
import com.taceiq.repository.*;
import com.taceiq.security.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class FindingEvidenceTraceabilityService {
    private final InvestigationRepository investigationRepository;
    private final InvestigationFindingRepository findingRepository;
    private final InvestigationFindingEvidenceRepository findingEvidenceRepository;
    private final EvidenceCorrelationProvenanceRepository provenanceRepository;
    private final IngestedSourceRecordRepository sourceRecordRepository;
    private final AuthorizationService authorizationService;
    private final ObjectMapper objectMapper;

    public FindingEvidenceTraceabilityService(InvestigationRepository investigationRepository,
                                               InvestigationFindingRepository findingRepository,
                                               InvestigationFindingEvidenceRepository findingEvidenceRepository,
                                               EvidenceCorrelationProvenanceRepository provenanceRepository,
                                               IngestedSourceRecordRepository sourceRecordRepository,
                                               AuthorizationService authorizationService,
                                               ObjectMapper objectMapper) {
        this.investigationRepository = investigationRepository;
        this.findingRepository = findingRepository;
        this.findingEvidenceRepository = findingEvidenceRepository;
        this.provenanceRepository = provenanceRepository;
        this.sourceRecordRepository = sourceRecordRepository;
        this.authorizationService = authorizationService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public FindingEvidenceTraceabilityResponse trace(Long investigationId, Long findingId) {
        authorizationService.requireAnyPermission("EVIDENCE_GRAPH_ACCESS", "WORKSPACE_ACCESS", "INVESTIGATION_ACCESS");
        Long orgId = authorizationService.getCurrentOrgId();
        investigationRepository.findByIdAndOrganisationOrgId(investigationId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Investigation not found"));
        InvestigationFinding finding = findingRepository.findByIdAndOrganisationOrgIdAndInvestigationId(findingId, orgId, investigationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Finding not found"));

        List<FindingEvidenceTraceabilityResponse.EvidenceTrace> evidence = findingEvidenceRepository
                .findByOrganisationOrgIdAndInvestigationIdAndFindingId(orgId, investigationId, findingId).stream()
                .map(link -> traceEvidence(link, orgId, investigationId))
                .toList();
        return FindingEvidenceTraceabilityResponse.builder().investigationId(investigationId).findingId(finding.getId()).evidence(evidence).build();
    }

    private FindingEvidenceTraceabilityResponse.EvidenceTrace traceEvidence(InvestigationFindingEvidence link, Long orgId, Long investigationId) {
        CanonicalEvidence evidence = link.getCanonicalEvidence();
        List<FindingEvidenceTraceabilityResponse.DiscoveryPath> paths = provenanceRepository
                .findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(orgId, investigationId, evidence.getId()).stream()
                .map(this::path)
                .toList();
        String sourceType = evidence.getSourceType();
        String sourceRecordId = sourceRecordId(evidence);
        IngestedSourceRecord source = sourceRecordRepository.findByOrganisationOrgIdAndSourceTypeAndSourceRecordId(orgId, sourceType, sourceRecordId).orElse(null);
        return FindingEvidenceTraceabilityResponse.EvidenceTrace.builder().evidenceId(evidence.getExternalId())
                .sourceSystem(sourceType).sourceRecordId(sourceRecordId).title(evidence.getTitle())
                .findingRelationship(link.getRelationshipType()).discoveryPaths(paths).sourceRecord(source == null ? null : sourceRecord(source)).build();
    }

    private FindingEvidenceTraceabilityResponse.DiscoveryPath path(EvidenceCorrelationProvenance provenance) {
        List<String> values = parsePath(provenance.getConnectionPath());
        return FindingEvidenceTraceabilityResponse.DiscoveryPath.builder().classification(classification(provenance.getReason()))
                .reason(provenance.getReason()).matchedField(provenance.getMatchedField()).matchedValue(provenance.getMatchedValue())
                .intermediateEntityType(provenance.getIntermediateEntityType()).intermediateEntityValue(provenance.getIntermediateEntityValue())
                .path(values).discoveredAt(provenance.getDiscoveredAt()).build();
    }

    private FindingEvidenceTraceabilityResponse.SourceRecord sourceRecord(IngestedSourceRecord source) {
        File file = source.getSourceFile();
        return FindingEvidenceTraceabilityResponse.SourceRecord.builder().id(source.getId()).sourceType(source.getSourceType())
                .sourceRecordId(source.getSourceRecordId()).batchReference(source.getBatchReference()).machineReference(source.getMachineReference())
                .supplierReference(source.getSupplierReference()).productReference(source.getProductReference()).orderReference(source.getOrderReference())
                .externalReference(source.getExternalReference()).payload(source.getPayload()).ingestedAt(source.getIngestedAt())
                .sourceFile(file == null ? null : FindingEvidenceTraceabilityResponse.SourceFile.builder().id(file.getId()).originalName(file.getOriginalName())
                        .contentType(file.getContentType()).size(file.getSize()).receivedAt(file.getReceivedAt()).build()).build();
    }

    private List<String> parsePath(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try { return objectMapper.readValue(raw, new TypeReference<List<String>>() {}).stream().filter(value -> !value.startsWith("Incident:")).toList(); } catch (Exception ignored) { return List.of(); }
    }

    private String sourceRecordId(CanonicalEvidence evidence) {
        try {
            Map<String, Object> payload = objectMapper.readValue(evidence.getNormalizedPayload() == null ? "{}" : evidence.getNormalizedPayload(), new TypeReference<>() {});
            for (String key : List.of("source_record_id", "sourceRecordId", "record_id")) if (payload.get(key) != null) return String.valueOf(payload.get(key));
        } catch (Exception ignored) { }
        String externalId = evidence.getExternalId();
        if (externalId != null && externalId.startsWith("SRC_")) {
            String prefix = "SRC_" + (evidence.getSourceType() == null ? "" : evidence.getSourceType()) + "_";
            if (externalId.startsWith(prefix)) return externalId.substring(prefix.length());
        }
        return externalId;
    }

    private String classification(String reason) {
        if (reason == null) return "CROSS_BATCH_CONTEXT";
        return Set.of("DIRECT_BATCH_MATCH", "MACHINE_MATCH_FROM_BATCH", "SUPPLIER_MATCH_FROM_BATCH", "PRODUCT_MATCH_FROM_BATCH",
                "SHIPMENT_MATCH_FROM_BATCH", "WAREHOUSE_MATCH_FROM_BATCH", "QA_MATCH_FROM_BATCH").contains(reason)
                ? "PRIMARY" : "CROSS_BATCH_CONTEXT";
    }
}
