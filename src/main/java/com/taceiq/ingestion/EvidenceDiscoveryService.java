package com.taceiq.ingestion;

import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.EvidenceCorrelationProvenance;
import com.taceiq.entity.IngestedSourceRecord;
import com.taceiq.entity.Investigation;
import com.taceiq.entity.Organisation;
import com.taceiq.entity.InvestigationEvidence;
import com.taceiq.graph.service.GraphProjectionService;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.IngestedSourceRecordRepository;
import com.taceiq.repository.InvestigationEvidenceRepository;
import com.taceiq.repository.EvidenceCorrelationProvenanceRepository;
import com.taceiq.repository.InvestigationRepository;
import com.taceiq.repository.OrganisationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EvidenceDiscoveryService {

    private final InvestigationRepository investigationRepository;
    private final IngestedSourceRecordRepository sourceRecordRepository;
    private final CanonicalEvidenceRepository canonicalEvidenceRepository;
    private final OrganisationRepository organisationRepository;
    private final GraphProjectionService graphProjectionService;
    private final SourceRecordIngestionService ingestionService;
    private final SourceFieldMapper fieldMapper;
    private final ObjectMapper objectMapper;
    private final InvestigationEvidenceRepository investigationEvidenceRepository;
    private final EvidenceCorrelationProvenanceRepository provenanceRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public EvidenceDiscoveryService(InvestigationRepository investigationRepository,
                                    IngestedSourceRecordRepository sourceRecordRepository,
                                    CanonicalEvidenceRepository canonicalEvidenceRepository,
                                    OrganisationRepository organisationRepository,
                                    GraphProjectionService graphProjectionService,
                                    SourceRecordIngestionService ingestionService,
                                    SourceFieldMapper fieldMapper,
                                    ObjectMapper objectMapper,
                                    InvestigationEvidenceRepository investigationEvidenceRepository,
                                    EvidenceCorrelationProvenanceRepository provenanceRepository) {
        this.investigationRepository = investigationRepository;
        this.sourceRecordRepository = sourceRecordRepository;
        this.canonicalEvidenceRepository = canonicalEvidenceRepository;
        this.organisationRepository = organisationRepository;
        this.graphProjectionService = graphProjectionService;
        this.ingestionService = ingestionService;
        this.fieldMapper = fieldMapper;
        this.objectMapper = objectMapper;
        this.investigationEvidenceRepository = investigationEvidenceRepository;
        this.provenanceRepository = provenanceRepository;
    }

    // Backward compat for tests with 8 args (without InvestigationEvidenceRepository)
    public EvidenceDiscoveryService(InvestigationRepository investigationRepository,
                                    IngestedSourceRecordRepository sourceRecordRepository,
                                    CanonicalEvidenceRepository canonicalEvidenceRepository,
                                    OrganisationRepository organisationRepository,
                                    GraphProjectionService graphProjectionService,
                                    SourceRecordIngestionService ingestionService,
                                    SourceFieldMapper fieldMapper,
                                    ObjectMapper objectMapper) {
        this(investigationRepository, sourceRecordRepository, canonicalEvidenceRepository, organisationRepository, graphProjectionService, ingestionService, fieldMapper, objectMapper, null, null);
    }

    public EvidenceDiscoveryService(InvestigationRepository investigationRepository,
                                    IngestedSourceRecordRepository sourceRecordRepository,
                                    CanonicalEvidenceRepository canonicalEvidenceRepository,
                                    OrganisationRepository organisationRepository,
                                    GraphProjectionService graphProjectionService,
                                    SourceRecordIngestionService ingestionService,
                                    SourceFieldMapper fieldMapper,
                                    ObjectMapper objectMapper,
                                    InvestigationEvidenceRepository investigationEvidenceRepository) {
        this(investigationRepository, sourceRecordRepository, canonicalEvidenceRepository, organisationRepository, graphProjectionService, ingestionService, fieldMapper, objectMapper, investigationEvidenceRepository, null);
    }

    public static class DiscoveryResult {
        public Long incidentId;
        public String batchReference;
        public int discovered;
        public int created;
        public int reused;
        public Map<String, Integer> sources = new LinkedHashMap<>();
        public Map<String, Integer> reasons = new LinkedHashMap<>();
        public boolean graphProjected;
        public String graphMessage;
        public List<String> warnings = new ArrayList<>();
    }

    @Transactional
    public DiscoveryResult discoverForIncident(Long incidentId, Long orgId) {
        Investigation incident = investigationRepository.findByIdAndOrganisationOrgId(incidentId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found with id: " + incidentId));
        String batchRef = incident.getBatchReference();
        if (batchRef == null || batchRef.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incident batchReference is required for discovery");
        }
        batchRef = batchRef.trim();
        DiscoveryResult result = new DiscoveryResult();
        result.incidentId = incidentId;
        result.batchReference = batchRef;

        // Lazy ingestion: if no source records for org, try to ingest existing files
        long existingCount = sourceRecordRepository.countByOrganisationOrgId(orgId);
        if (existingCount == 0) {
            try {
                var ing = ingestionService.ingestExistingFilesForOrg(orgId);
                if (ing.created > 0) {
                    log.info("Lazy ingested {} source records for org {}", ing.created, orgId);
                    result.warnings.add("Ingested " + ing.created + " source records from existing files");
                }
            } catch (Exception e) {
                log.warn("Lazy ingestion failed for org {}: {}", orgId, e.getMessage());
            }
        }

        // Direct batch matches
        List<IngestedSourceRecord> direct = sourceRecordRepository.findByOrganisationOrgIdAndBatchReference(orgId, batchRef);
        // Also try case-insensitive? For now exact trim; we already normalized batchReference via ingestion (trimmed)
        // Filter to ensure exact match after trim (already)
        Map<String, IngestedSourceRecord> uniqueDirect = new LinkedHashMap<>();
        Map<String, Set<String>> reasonsForKey = new LinkedHashMap<>();
        for (IngestedSourceRecord r : direct) {
            String key = r.getSourceType() + "|" + r.getSourceRecordId();
            uniqueDirect.putIfAbsent(key, r);
            reasonsForKey.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add("DIRECT_BATCH_MATCH");
        }

        // Collect related identifiers directly used by THIS batch
        Set<String> machineIds = new LinkedHashSet<>();
        Set<String> supplierIds = new LinkedHashSet<>();
        Set<String> productIds = new LinkedHashSet<>();
        for (IngestedSourceRecord r : uniqueDirect.values()) {
            if (r.getMachineReference() != null && !r.getMachineReference().isBlank()) machineIds.add(r.getMachineReference().trim());
            if (r.getSupplierReference() != null && !r.getSupplierReference().isBlank()) supplierIds.add(r.getSupplierReference().trim());
            if (r.getProductReference() != null && !r.getProductReference().isBlank()) productIds.add(r.getProductReference().trim());
        }

        Map<String, Set<String>> productToMachines = new HashMap<>();
        Set<String> directMachineIds = new HashSet<>(machineIds);

        Map<String, IngestedSourceRecord> related = new LinkedHashMap<>();
        Map<String, String> reasonForKey = new HashMap<>(); // key -> reason

        // 1. Fetch CMMS/Maintenance/SOP/Audit records for machines that processed THIS batch
        for (String mid : machineIds) {
            if (mid.isBlank()) continue;
            List<IngestedSourceRecord> cmms = sourceRecordRepository.findByOrganisationOrgIdAndMachineReference(orgId, mid);
            for (IngestedSourceRecord r : cmms) {
                // Do NOT include production batch records for other batches!
                if ("MES".equalsIgnoreCase(r.getSourceType()) || "PRODUCTION".equalsIgnoreCase(r.getSourceType())) {
                    if (r.getBatchReference() != null && !batchRef.equalsIgnoreCase(r.getBatchReference().trim())) {
                        continue; // Skip unrelated production batch records
                    }
                }
                String key = r.getSourceType() + "|" + r.getSourceRecordId();
                reasonsForKey.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add("MACHINE_MATCH_FROM_BATCH");
                if (!uniqueDirect.containsKey(key)) {
                    related.putIfAbsent(key, r);
                    reasonForKey.putIfAbsent(key, "MACHINE_MATCH_FROM_BATCH");
                }
            }
        }

        // 2. Fetch ERP/Supplier records for suppliers linked to THIS batch
        for (String sid : supplierIds) {
            if (sid.isBlank()) continue;
            List<IngestedSourceRecord> erp = sourceRecordRepository.findByOrganisationOrgIdAndSupplierReference(orgId, sid);
            for (IngestedSourceRecord r : erp) {
                if ("MES".equalsIgnoreCase(r.getSourceType()) || "PRODUCTION".equalsIgnoreCase(r.getSourceType())) {
                    if (r.getBatchReference() != null && !batchRef.equalsIgnoreCase(r.getBatchReference().trim())) {
                        continue; // Skip unrelated production batch records
                    }
                }
                String key = r.getSourceType() + "|" + r.getSourceRecordId();
                reasonsForKey.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add("SUPPLIER_MATCH_FROM_BATCH");
                if (!uniqueDirect.containsKey(key)) {
                    related.putIfAbsent(key, r);
                    reasonForKey.putIfAbsent(key, "SUPPLIER_MATCH_FROM_BATCH");
                }
            }
        }

        // 3. Fetch product-level records strictly via SAME source record (batch->product)
        // Do NOT infer product -> every machine/CRM that shares product. Only direct batch matches or product master (ERP without batch) are primary.
        // CRM complaints must reference the current batch explicitly to be primary; sharing product alone is CROSS_BATCH_CONTEXT.
        for (String pid : productIds) {
            if (pid.isBlank()) continue;
            List<IngestedSourceRecord> prodRecords = sourceRecordRepository.findByOrganisationOrgIdAndProductReference(orgId, pid);
            for (IngestedSourceRecord r : prodRecords) {
                if (r.getMachineReference() != null && !r.getMachineReference().isBlank()) {
                    productToMachines.computeIfAbsent(pid, k -> new HashSet<>()).add(r.getMachineReference().trim());
                }
                boolean isBatchMatch = r.getBatchReference() != null && batchRef.equalsIgnoreCase(r.getBatchReference().trim());
                boolean isProductMaster = ("ERP".equalsIgnoreCase(r.getSourceType()) || "ROUTING".equalsIgnoreCase(r.getSourceType())) && r.getBatchReference() == null;
                if (isBatchMatch || isProductMaster) {
                    String key = r.getSourceType() + "|" + r.getSourceRecordId();
                    reasonsForKey.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add("PRODUCT_MATCH_FROM_BATCH");
                    if (!uniqueDirect.containsKey(key)) {
                        related.putIfAbsent(key, r);
                        reasonForKey.putIfAbsent(key, "PRODUCT_MATCH_FROM_BATCH");
                    }
                }
            }
        }

        // 4. Cross-batch machine expansion via Product is NOT performed.
        // Previous logic inferred Product -> every machine ever used by that product,
        // which incorrectly promoted M-07 to BATCH-1010 because BATCH-1019 also uses PRD-106.
        // Primary expansion is limited to explicitly supported relationships:
        //   batch -> product, batch -> machine, batch -> supplier (from MES BATCH-1010 record)
        // Machines not directly referenced by BATCH-1010 must be CROSS_BATCH_CONTEXT,
        // not PRIMARY. See InvestigationEvidenceService classification.

        // Combine all discovered
        List<IngestedSourceRecord> allDiscovered = new ArrayList<>();
        allDiscovered.addAll(uniqueDirect.values());
        allDiscovered.addAll(related.values());
        result.discovered = allDiscovered.size();

        // Create canonical evidence for each discovered record
        Organisation orgRef = organisationRepository.getReferenceById(orgId);
        for (IngestedSourceRecord src : allDiscovered) {
            String reason;
            String key = src.getSourceType() + "|" + src.getSourceRecordId();
            Set<String> matchReasons = reasonsForKey.getOrDefault(key, new LinkedHashSet<>());
            if (matchReasons.isEmpty()) {
                matchReasons.add(uniqueDirect.containsKey(key) ? "DIRECT_BATCH_MATCH" : reasonForKey.getOrDefault(key, "RELATED_MATCH"));
            }
            reason = matchReasons.iterator().next();

            String externalId = "SRC_" + src.getSourceType() + "_" + src.getSourceRecordId();
            externalId = externalId.trim().replaceAll("\\s+", "_");
            // Ensure length safe
            if (externalId.length() > 200) externalId = externalId.substring(0, 200);

            Optional<CanonicalEvidence> existingOpt = canonicalEvidenceRepository
                    .findByExternalIdAndOrganisationOrgId(externalId, orgId);
            CanonicalEvidence evidence;
            boolean isNew = false;
            if (existingOpt.isPresent()) {
                evidence = existingOpt.get();
                // Ensure incident association via canonical.incident_id if null, otherwise via join table for cross-incident reuse
                if (evidence.getIncident() == null) {
                    evidence.setIncident(incident);
                    evidence.setLastSeenAt(Instant.now());
                    canonicalEvidenceRepository.save(evidence);
                } else if (!evidence.getIncident().getId().equals(incidentId)) {
                    // Cross-incident reuse: ensure investigation_evidence link exists (idempotent)
                    if (investigationEvidenceRepository != null) {
                        boolean alreadyLinked = investigationEvidenceRepository.existsByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(orgId, incidentId, evidence.getId());
                        if (!alreadyLinked) {
                            try {
                                InvestigationEvidence link = InvestigationEvidence.builder()
                                        .organisation(organisationRepository.getReferenceById(orgId))
                                        .investigation(incident)
                                        .canonicalEvidence(evidence)
                                        .build();
                                investigationEvidenceRepository.save(link);
                            } catch (Exception e) {
                                log.warn("Failed to create cross-incident link for {} incident {}: {}", externalId, incidentId, e.getMessage());
                            }
                        }
                    }
                } else {
                    // Same incident, just bump lastSeen
                    evidence.setLastSeenAt(Instant.now());
                    canonicalEvidenceRepository.save(evidence);
                }
                result.reused++;
            } else {
                // Create new canonical evidence
                String title = src.getSourceRecordId();
                if (src.getPayload() != null) {
                    try {
                        Map<String,Object> payloadMap = objectMapper.readValue(src.getPayload(), Map.class);
                        // Try to get title from payload fields
                        for (String k : List.of("title","description","product_name","test_type","status","record_id")) {
                            if (payloadMap.containsKey(k) && payloadMap.get(k) != null) {
                                title = payloadMap.get(k).toString();
                                break;
                            }
                        }
                        if (title == null || title.isBlank()) title = src.getSourceRecordId();
                    } catch (Exception ignored) {}
                }
                // Build normalized payload with provenance
                Map<String,Object> norm = new LinkedHashMap<>();
                norm.put("sourceType", src.getSourceType());
                norm.put("sourceRecordId", src.getSourceRecordId());
                norm.put("batchReference", src.getBatchReference());
                norm.put("machineReference", src.getMachineReference());
                norm.put("supplierReference", src.getSupplierReference());
                norm.put("productReference", src.getProductReference());
                norm.put("correlationReason", reason);
                norm.put("originalPayload", src.getPayload());
                String normalizedPayload;
                try { normalizedPayload = objectMapper.writeValueAsString(norm); } catch (Exception e) { normalizedPayload = "{}"; }
                String contentHash = sha256Hex(normalizedPayload);

                evidence = CanonicalEvidence.builder()
                        .organisation(orgRef)
                        .integration(null)
                        .incident(incident)
                        .externalId(externalId)
                        .title(title)
                        .sourceType(src.getSourceType())
                        .status("READY")
                        .contentHash(contentHash)
                        .normalizedPayload(normalizedPayload)
                        .firstSeenAt(Instant.now())
                        .lastSeenAt(Instant.now())
                        .isDeleted(false)
                        .build();
                canonicalEvidenceRepository.save(evidence);
                result.created++;
                isNew = true;
            }
            // Update source breakdown
            result.sources.merge(src.getSourceType(), 1, Integer::sum);
            for (String matchReason : matchReasons) {
                result.reasons.merge(matchReason, 1, Integer::sum);
                recordProvenance(orgId, incident, evidence, src, matchReason, batchRef, productToMachines, directMachineIds);
            }
        }

        // Trigger graph projection best-effort
        try {
            graphProjectionService.projectForOrganisation(orgId);
            result.graphProjected = true;
            result.graphMessage = "Graph projection succeeded";
        } catch (Exception e) {
            result.graphProjected = false;
            result.graphMessage = "Graph projection failed: " + e.getMessage();
            log.warn("Graph projection after discovery failed for incident {} org {}: {}", incidentId, orgId, e.getMessage());
        }

        return result;
    }

    private void recordProvenance(Long orgId, Investigation incident, CanonicalEvidence evidence,
                                  IngestedSourceRecord source, String reason, String batchReference,
                                  Map<String, Set<String>> productToMachines, Set<String> directMachineIds) {
        if (provenanceRepository == null || evidence.getId() == null) return;

        String matchedField = null;
        String matchedValue = null;
        String intermediateType = null;
        String intermediateValue = null;
        List<String> path = new ArrayList<>();
        String incidentLabel = incident.getInvestigationKey() != null
                ? incident.getInvestigationKey() : String.valueOf(incident.getId());
        path.add("Incident:" + incidentLabel);

        if ("DIRECT_BATCH_MATCH".equals(reason)) {
            matchedField = resolveSourceField(source, SourceFieldMapper.BATCH);
            matchedValue = batchReference;
            path.add("Batch:" + batchReference);
            String zone = extractPayloadField(source, "warehouse_zone", "warehouseZone", "zone");
            if (zone != null && !zone.isBlank()) {
                intermediateType = "warehouse";
                intermediateValue = zone;
                path.add("Warehouse:" + zone);
            } else if ("SHIPMENT".equalsIgnoreCase(source.getSourceType())) {
                intermediateType = "shipment";
                intermediateValue = source.getSourceRecordId();
                path.add("Shipment:" + source.getSourceRecordId());
                String cust = extractPayloadField(source, "customer_id", "customerId", "customer");
                if (cust != null && !cust.isBlank()) {
                    path.add("Customer:" + cust);
                }
            } else if ("CRM".equalsIgnoreCase(source.getSourceType()) && source.getProductReference() != null) {
                intermediateType = "product";
                intermediateValue = source.getProductReference();
                path.add("Product:" + source.getProductReference());
            } else if (source.getMachineReference() != null && !source.getMachineReference().isBlank()) {
                intermediateType = "machine";
                intermediateValue = source.getMachineReference();
                path.add("Machine:" + source.getMachineReference());
            } else if (source.getSupplierReference() != null && !source.getSupplierReference().isBlank()) {
                intermediateType = "supplier";
                intermediateValue = source.getSupplierReference();
                path.add("Supplier:" + source.getSupplierReference());
            }
        } else if ("MACHINE_MATCH_FROM_BATCH".equals(reason)) {
            matchedField = resolveSourceField(source, SourceFieldMapper.MACHINE);
            matchedValue = source.getMachineReference();
            intermediateType = "machine";
            intermediateValue = source.getMachineReference();
            path.add("Batch:" + batchReference);
            path.add("Machine:" + source.getMachineReference());
        } else if ("PRODUCT_MACHINE_MATCH".equals(reason)) {
            matchedField = resolveSourceField(source, SourceFieldMapper.MACHINE);
            matchedValue = source.getMachineReference();
            intermediateType = "machine";
            intermediateValue = source.getMachineReference();
            path.add("Batch:" + batchReference);
            String prodId = findProductForMachine(source.getMachineReference(), productToMachines);
            if (prodId != null) {
                path.add("Product:" + prodId);
            }
            path.add("Machine:" + source.getMachineReference());
        } else if ("SUPPLIER_MATCH_FROM_BATCH".equals(reason)) {
            matchedField = resolveSourceField(source, SourceFieldMapper.SUPPLIER);
            matchedValue = source.getSupplierReference();
            intermediateType = "supplier";
            intermediateValue = source.getSupplierReference();
            path.add("Batch:" + batchReference);
            path.add("Supplier:" + source.getSupplierReference());
        } else if ("PRODUCT_MATCH_FROM_BATCH".equals(reason)) {
            matchedField = resolveSourceField(source, SourceFieldMapper.PRODUCT);
            matchedValue = source.getProductReference();
            intermediateType = "product";
            intermediateValue = source.getProductReference();
            path.add("Batch:" + batchReference);
            path.add("Product:" + source.getProductReference());
        }
        path.add(source.getSourceType() + " Evidence:" + source.getSourceRecordId());

        saveProvenance(orgId, incident, evidence, source, reason, matchedField, matchedValue, intermediateType, intermediateValue, path);

        // If machine was direct to batch AND also belongs to a product, also save the indirect path so both valid paths exist
        if ("MACHINE_MATCH_FROM_BATCH".equals(reason) && source.getMachineReference() != null) {
            String prodId = findProductForMachine(source.getMachineReference(), productToMachines);
            if (prodId != null) {
                List<String> prodPath = new ArrayList<>();
                prodPath.add("Incident:" + incidentLabel);
                prodPath.add("Batch:" + batchReference);
                prodPath.add("Product:" + prodId);
                prodPath.add("Machine:" + source.getMachineReference());
                prodPath.add(source.getSourceType() + " Evidence:" + source.getSourceRecordId());
                saveProvenance(orgId, incident, evidence, source, "PRODUCT_MACHINE_MATCH", matchedField, matchedValue, "machine", source.getMachineReference(), prodPath);
            }
        }
    }

    private void saveProvenance(Long orgId, Investigation incident, CanonicalEvidence evidence,
                                IngestedSourceRecord source, String reason, String matchedField, String matchedValue,
                                String intermediateType, String intermediateValue, List<String> path) {
        String connectionPath;
        try {
            connectionPath = objectMapper.writeValueAsString(path);
        } catch (Exception e) {
            connectionPath = "[]";
        }
        final String finalMatchedField = matchedField;
        final String finalMatchedValue = matchedValue;
        final String finalIntermediateType = intermediateType;
        final String finalIntermediateValue = intermediateValue;
        List<EvidenceCorrelationProvenance> existing = provenanceRepository
                .findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(orgId, incident.getId(), evidence.getId());
        boolean duplicate = existing.stream().anyMatch(p -> Objects.equals(p.getReason(), reason)
            && Objects.equals(p.getMatchedField(), finalMatchedField)
            && Objects.equals(p.getMatchedValue(), finalMatchedValue)
                && Objects.equals(p.getSourceRecordId(), source.getSourceRecordId())
            && Objects.equals(p.getIntermediateEntityType(), finalIntermediateType)
            && Objects.equals(p.getIntermediateEntityValue(), finalIntermediateValue));
        if (duplicate) return;
        provenanceRepository.save(EvidenceCorrelationProvenance.builder()
                .organisation(organisationRepository.getReferenceById(orgId))
                .investigation(incident)
                .canonicalEvidence(evidence)
                .reason(reason)
                .matchedField(finalMatchedField)
                .matchedValue(finalMatchedValue)
                .sourceRecordId(source.getSourceRecordId())
                .intermediateEntityType(finalIntermediateType)
                .intermediateEntityValue(finalIntermediateValue)
                .connectionPath(connectionPath)
                .discoveredAt(Instant.now())
                .build());
    }

    private String extractPayloadField(IngestedSourceRecord source, String... keys) {
        if (source.getPayload() == null) return null;
        try {
            Map<String, Object> map = objectMapper.readValue(source.getPayload(), Map.class);
            for (String k : keys) {
                if (map.containsKey(k) && map.get(k) != null) {
                    return map.get(k).toString().trim();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String findProductForMachine(String mid, Map<String, Set<String>> productToMachines) {
        if (productToMachines == null || mid == null) return null;
        for (Map.Entry<String, Set<String>> e : productToMachines.entrySet()) {
            if (e.getValue().contains(mid)) return e.getKey();
        }
        return null;
    }

    private String resolveSourceField(IngestedSourceRecord source, String normalizedField) {
        if (source.getPayload() != null) {
            try {
                Map<String, Object> raw = objectMapper.readValue(source.getPayload(), Map.class);
                for (String key : raw.keySet()) {
                    if (normalizedField.equals(fieldMapper.mapToNormalized(key))) return key;
                }
            } catch (Exception ignored) {}
        }
        return normalizedField.toLowerCase();
    }

    private String sha256Hex(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
