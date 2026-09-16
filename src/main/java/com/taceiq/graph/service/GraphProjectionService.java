package com.taceiq.graph.service;

import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.Investigation;
import com.taceiq.entity.InvestigationEvidence;
import com.taceiq.graph.config.Neo4jConfig;
import com.taceiq.graph.dto.GraphProjectionResult;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.InvestigationRepository;
import com.taceiq.repository.InvestigationEvidenceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GraphProjectionService {

    private final CanonicalEvidenceRepository canonicalRepo;
    private final Driver neo4jDriver;
    private final Neo4jConfig neo4jConfig;
    private final InvestigationRepository investigationRepository;
    private final InvestigationEvidenceRepository investigationEvidenceRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public GraphProjectionService(CanonicalEvidenceRepository canonicalRepo, Driver neo4jDriver, Neo4jConfig neo4jConfig, InvestigationRepository investigationRepository, InvestigationEvidenceRepository investigationEvidenceRepository) {
        this.canonicalRepo = canonicalRepo;
        this.neo4jDriver = neo4jDriver;
        this.neo4jConfig = neo4jConfig;
        this.investigationRepository = investigationRepository;
        this.investigationEvidenceRepository = investigationEvidenceRepository;
    }

    // Backward compat for tests that construct with 3 args (without InvestigationRepository)
    public GraphProjectionService(CanonicalEvidenceRepository canonicalRepo, Driver neo4jDriver, Neo4jConfig neo4jConfig) {
        this(canonicalRepo, neo4jDriver, neo4jConfig, null, null);
    }

    public GraphProjectionService(CanonicalEvidenceRepository canonicalRepo, Driver neo4jDriver, Neo4jConfig neo4jConfig, InvestigationRepository investigationRepository) {
        this(canonicalRepo, neo4jDriver, neo4jConfig, investigationRepository, null);
    }

    public GraphProjectionResult projectForOrganisation(Long orgId) {
        return projectInternal(orgId, null);
    }

    public GraphProjectionResult projectForIntegration(Long orgId, Long integrationId) {
        return projectInternal(orgId, integrationId);
    }

    public GraphProjectionResult rebuildForOrganisation(Long orgId) {
        if (neo4jDriver == null || neo4jConfig == null || !neo4jConfig.isConfigured()) {
            throw new IllegalStateException("Neo4j not configured (app.neo4j.uri missing) – cannot rebuild graph");
        }
        String database = neo4jConfig.getDatabase() != null ? neo4jConfig.getDatabase() : "neo4j";
        try (Session session = neo4jDriver.session(org.neo4j.driver.SessionConfig.forDatabase(database))) {
            session.executeWrite(tx -> {
                tx.run("MATCH (n {orgId: $orgId}) DETACH DELETE n", Map.of("orgId", orgId));
                return null;
            });
            log.info("Purged graph nodes for organisation {}", orgId);
        } catch (Exception e) {
            log.warn("Failed to purge graph for organisation {}: {}", orgId, e.getMessage());
            throw new RuntimeException("Failed to purge graph for organisation " + orgId + ": " + e.getMessage(), e);
        }
        return projectForOrganisation(orgId);
    }

    private GraphProjectionResult projectInternal(Long orgId, Long integrationId) {
        if (neo4jDriver == null || neo4jConfig == null || !neo4jConfig.isConfigured()) {
            throw new IllegalStateException("Neo4j not configured (app.neo4j.uri missing) – cannot project graph");
        }
        List<CanonicalEvidence> evidences;
        if (integrationId != null) {
            evidences = canonicalRepo.findByIntegrationIdAndOrganisationOrgId(integrationId, orgId);
        } else {
            evidences = canonicalRepo.findAllByOrganisationOrgId(orgId);
        }
        // Filter is_deleted = false (repository may return deleted, but we enforce)
        List<CanonicalEvidence> active = evidences.stream().filter(e -> !Boolean.TRUE.equals(e.getIsDeleted())).toList();

        int skipped = evidences.size() - active.size();
        Set<String> seenCases = new HashSet<>();
        Set<String> seenActors = new HashSet<>();
        Set<Long> seenIncidents = new HashSet<>();
        int relationships = 0;
        List<InvestigationEvidence> explicitLinks = investigationEvidenceRepository == null
            ? List.of() : investigationEvidenceRepository.findByOrganisationOrgId(orgId);

        String database = neo4jConfig != null ? neo4jConfig.getDatabase() : "neo4j";

        try (Session session = neo4jDriver.session(org.neo4j.driver.SessionConfig.forDatabase(database))) {
            // Ensure compound uniqueness constraints (tenant-safe: orgId + stableId) — best effort, warn-only
            try {
                session.executeWrite(tx -> {
                    tx.run("CREATE CONSTRAINT incident_org_stable_unique IF NOT EXISTS FOR (i:Incident) REQUIRE (i.orgId, i.stableId) IS UNIQUE");
                    tx.run("CREATE CONSTRAINT evidence_org_stable_unique IF NOT EXISTS FOR (e:Evidence) REQUIRE (e.orgId, e.stableId) IS UNIQUE");
                    tx.run("CREATE CONSTRAINT batch_org_stable_unique IF NOT EXISTS FOR (b:Batch) REQUIRE (b.orgId, b.stableId) IS UNIQUE");
                    tx.run("CREATE CONSTRAINT machine_org_stable_unique IF NOT EXISTS FOR (m:Machine) REQUIRE (m.orgId, m.stableId) IS UNIQUE");
                    tx.run("CREATE CONSTRAINT supplier_org_stable_unique IF NOT EXISTS FOR (s:Supplier) REQUIRE (s.orgId, s.stableId) IS UNIQUE");
                    tx.run("CREATE CONSTRAINT product_org_stable_unique IF NOT EXISTS FOR (p:Product) REQUIRE (p.orgId, p.stableId) IS UNIQUE");
                    tx.run("CREATE CONSTRAINT customer_org_stable_unique IF NOT EXISTS FOR (c:Customer) REQUIRE (c.orgId, c.stableId) IS UNIQUE");
                    tx.run("CREATE CONSTRAINT warehouse_org_stable_unique IF NOT EXISTS FOR (w:Warehouse) REQUIRE (w.orgId, w.stableId) IS UNIQUE");
                    tx.run("CREATE CONSTRAINT case_org_stable_unique IF NOT EXISTS FOR (c:Case) REQUIRE (c.orgId, c.stableId) IS UNIQUE");
                    tx.run("CREATE CONSTRAINT actor_org_stable_unique IF NOT EXISTS FOR (a:Actor) REQUIRE (a.orgId, a.stableId) IS UNIQUE");
                    return null;
                });
            } catch (Exception e) {
                log.debug("Uniqueness constraint creation skipped: {}", e.getMessage());
            }

            // Prune stale Evidence nodes for this organisation that are no longer active in canonical evidence
            if (integrationId == null) {
                Set<String> activeExternalIds = active.stream()
                        .map(CanonicalEvidence::getExternalId)
                        .filter(id -> id != null && !id.isBlank())
                        .map(String::trim)
                        .collect(Collectors.toSet());
                try {
                    session.executeWrite(tx -> {
                        tx.run("""
                                MATCH (e:Evidence {orgId: $orgId})
                                WHERE NOT e.stableId IN $activeIds
                                DETACH DELETE e
                                """, Map.of("orgId", orgId, "activeIds", new ArrayList<>(activeExternalIds)));
                        return null;
                    });
                } catch (Exception e) {
                    log.warn("Stale evidence cleanup in Neo4j skipped for org {}: {}", orgId, e.getMessage());
                }
            }

            // Collect distinct incident IDs from active evidence that have incident association
            Set<Long> incidentIds = active.stream()
                    .filter(e -> e.getIncident() != null && e.getIncident().getId() != null)
                    .map(e -> e.getIncident().getId())
                    .collect(Collectors.toSet());
                    explicitLinks.stream()
                        .filter(link -> link.getInvestigation() != null && link.getInvestigation().getId() != null)
                        .map(link -> link.getInvestigation().getId())
                        .forEach(incidentIds::add);

            // Project Incident nodes (Phase 3) — idempotent MERGE, tenant-safe orgId+stableId
            for (Long incidentId : incidentIds) {
                try {
                    Investigation inc = null;
                    if (investigationRepository != null) {
                        inc = investigationRepository.findByIdAndOrganisationOrgId(incidentId, orgId).orElse(null);
                        // Fallback: try to load via canonical evidence incident proxy if direct load fails
                        if (inc == null) {
                            var opt = active.stream().filter(e -> e.getIncident() != null && incidentId.equals(e.getIncident().getId())).findFirst();
                            if (opt.isPresent() && opt.get().getIncident() != null) {
                                Investigation cand = opt.get().getIncident();
                                Long candOrg = cand.getOrganisation() != null ? cand.getOrganisation().getOrgId() : null;
                                if (orgId.equals(candOrg)) inc = cand;
                            }
                        }
                    } else {
                        // No repo (test) — use incident from canonical evidence proxy if matching org
                        var opt = active.stream().filter(e -> e.getIncident() != null && incidentId.equals(e.getIncident().getId())).findFirst();
                        if (opt.isPresent()) {
                            Investigation cand = opt.get().getIncident();
                            Long candOrg = cand.getOrganisation() != null ? cand.getOrganisation().getOrgId() : null;
                            if (candOrg == null || orgId.equals(candOrg)) inc = cand;
                        }
                    }
                    if (inc == null) continue;
                    // Verify organisation matches projection orgId (tenant isolation)
                    Long incOrgId = inc.getOrganisation() != null ? inc.getOrganisation().getOrgId() : null;
                    if (incOrgId != null && !incOrgId.equals(orgId)) continue;
                    final Investigation fInc = inc;
                    session.executeWrite(tx -> {
                        Map<String, Object> params = new java.util.HashMap<>();
                        params.put("orgId", orgId);
                        params.put("stableId", String.valueOf(fInc.getId()));
                        params.put("investigationKey", fInc.getInvestigationKey() != null ? fInc.getInvestigationKey() : "");
                        params.put("title", fInc.getTitle() != null ? fInc.getTitle() : "");
                        params.put("status", fInc.getStatus() != null ? fInc.getStatus() : "");
                        params.put("batchReference", fInc.getBatchReference() != null ? fInc.getBatchReference() : "");
                        params.put("productReference", fInc.getProductReference() != null ? fInc.getProductReference() : "");
                        params.put("orderReference", fInc.getOrderReference() != null ? fInc.getOrderReference() : "");
                        params.put("incidentStart", fInc.getIncidentStart() != null ? fInc.getIncidentStart().toString() : "");
                        params.put("incidentEnd", fInc.getIncidentEnd() != null ? fInc.getIncidentEnd().toString() : "");
                        tx.run("""
                                MERGE (i:Incident {orgId: $orgId, stableId: $stableId})
                                SET i.investigationKey = $investigationKey, i.title = $title, i.status = $status,
                                    i.batchReference = $batchReference, i.productReference = $productReference, i.orderReference = $orderReference,
                                    i.incidentStart = $incidentStart, i.incidentEnd = $incidentEnd
                                """, params);
                        return null;
                    });
                    seenIncidents.add(incidentId);

                    // Project target Batch node and TARGETS relationship if incident has batchReference
                    if (fInc.getBatchReference() != null && !fInc.getBatchReference().isBlank()) {
                        String incBatch = fInc.getBatchReference().trim();
                        session.executeWrite(tx -> {
                            tx.run("""
                                    MERGE (b:Batch {orgId: $orgId, stableId: $batchRef})
                                    SET b.title = $batchRef, b.name = $batchRef
                                    """, Map.of("orgId", orgId, "batchRef", incBatch));
                            tx.run("""
                                    MATCH (i:Incident {orgId: $orgId, stableId: $incidentId})
                                    MATCH (b:Batch {orgId: $orgId, stableId: $batchRef})
                                    MERGE (i)-[:TARGETS]->(b)
                                    """, Map.of("orgId", orgId, "incidentId", String.valueOf(fInc.getId()), "batchRef", incBatch));
                            return null;
                        });
                        relationships++;
                    }
                } catch (Exception e) {
                    log.warn("Incident projection failed for incident {} org {}: {}", incidentId, orgId, e.getMessage());
                }
            }

            List<Map<String, Object>> evidenceItems = new ArrayList<>();
            List<Map<String, Object>> incidentLinks = new ArrayList<>();
            List<Map<String, Object>> caseItems = new ArrayList<>();
            List<Map<String, Object>> actorItems = new ArrayList<>();
            List<Map<String, Object>> parentLinks = new ArrayList<>();
            List<Map<String, Object>> batchLinks = new ArrayList<>();
            List<Map<String, Object>> machineLinks = new ArrayList<>();
            List<Map<String, Object>> supplierLinks = new ArrayList<>();
            List<Map<String, Object>> productLinks = new ArrayList<>();
            List<Map<String, Object>> customerLinks = new ArrayList<>();
            List<Map<String, Object>> warehouseLinks = new ArrayList<>();
            List<Map<String, Object>> batchMachineLinks = new ArrayList<>();
            List<Map<String, Object>> batchSupplierLinks = new ArrayList<>();
            List<Map<String, Object>> batchProductLinks = new ArrayList<>();
            List<Map<String, Object>> batchCustomerLinks = new ArrayList<>();
            List<Map<String, Object>> batchWarehouseLinks = new ArrayList<>();

            for (CanonicalEvidence ev : active) {
                String externalId = ev.getExternalId();
                if (externalId == null || externalId.isBlank()) {
                    skipped++;
                    continue;
                }
                String trimmedExt = externalId.trim();
                evidenceItems.add(Map.of(
                        "stableId", trimmedExt,
                        "title", ev.getTitle() != null ? ev.getTitle() : "",
                        "sourceType", ev.getSourceType() != null ? ev.getSourceType() : "",
                        "status", ev.getStatus() != null ? ev.getStatus() : "",
                        "sourceCreatedAt", ev.getSourceCreatedAt() != null ? ev.getSourceCreatedAt().toString() : "",
                        "sourceUpdatedAt", ev.getSourceUpdatedAt() != null ? ev.getSourceUpdatedAt().toString() : ""
                ));

                if (ev.getIncident() != null && ev.getIncident().getId() != null) {
                    String incStable = String.valueOf(ev.getIncident().getId());
                    Long incOrgId = null;
                    try {
                        if (ev.getIncident().getOrganisation() != null) incOrgId = ev.getIncident().getOrganisation().getOrgId();
                    } catch (Exception ignored) {}
                    if (incOrgId == null || incOrgId.equals(orgId)) {
                        incidentLinks.add(Map.of("incidentId", incStable, "externalId", trimmedExt));
                    }
                }

                String caseId = ev.getCaseId();
                if (caseId != null && !caseId.isBlank()) {
                    String cId = caseId.trim();
                    seenCases.add(cId);
                    caseItems.add(Map.of("caseId", cId, "externalId", trimmedExt));
                }

                String actorId = ev.getActorId();
                if (actorId != null && !actorId.isBlank()) {
                    String aId = actorId.trim();
                    seenActors.add(aId);
                    actorItems.add(Map.of("actorId", aId, "externalId", trimmedExt));
                }

                String parentId = ev.getParentId();
                if (parentId != null && !parentId.isBlank()) {
                    String pId = parentId.trim();
                    if (!pId.equals(trimmedExt)) {
                        parentLinks.add(Map.of("parentId", pId, "externalId", trimmedExt));
                    }
                }

                // Multi-hop entity references
                String evBatch = null;
                String evMachine = null;
                String evSupplier = null;
                String evProduct = null;
                String evCustomer = null;
                String evWarehouse = null;

                if (ev.getNormalizedPayload() != null && !ev.getNormalizedPayload().isBlank()) {
                    try {
                        Map<String, Object> norm = objectMapper.readValue(ev.getNormalizedPayload(), Map.class);
                        evBatch = extractString(norm, "batchReference");
                        evMachine = extractString(norm, "machineReference");
                        evSupplier = extractString(norm, "supplierReference");
                        evProduct = extractString(norm, "productReference");

                        Object orig = norm.get("originalPayload");
                        Map<String, Object> origMap = null;
                        if (orig instanceof Map) {
                            origMap = (Map<String, Object>) orig;
                        } else if (orig instanceof String && !((String) orig).isBlank()) {
                            origMap = objectMapper.readValue((String) orig, Map.class);
                        }
                        if (origMap != null) {
                            evCustomer = extractString(origMap, "customer_id", "customerId", "customer");
                            evWarehouse = extractString(origMap, "warehouse_zone", "warehouseZone", "zone");
                            if (evBatch == null) evBatch = extractString(origMap, "batch_number", "batch_id", "batch", "batchReference");
                            if (evMachine == null) evMachine = extractString(origMap, "machine_id", "machine", "machineReference");
                            if (evSupplier == null) evSupplier = extractString(origMap, "supplier_id", "supplier", "supplierReference");
                            if (evProduct == null) evProduct = extractString(origMap, "product_id", "product_code", "product", "productReference");
                        }
                    } catch (Exception ignored) {}
                }

                if (evBatch != null) batchLinks.add(Map.of("id", evBatch, "externalId", trimmedExt));
                if (evMachine != null) machineLinks.add(Map.of("id", evMachine, "externalId", trimmedExt));
                if (evSupplier != null) supplierLinks.add(Map.of("id", evSupplier, "externalId", trimmedExt));
                if (evProduct != null) productLinks.add(Map.of("id", evProduct, "externalId", trimmedExt));
                if (evCustomer != null) customerLinks.add(Map.of("id", evCustomer, "externalId", trimmedExt));
                if (evWarehouse != null) warehouseLinks.add(Map.of("id", evWarehouse, "externalId", trimmedExt));

                if (evBatch != null) {
                    if (evMachine != null) batchMachineLinks.add(Map.of("bId", evBatch, "mId", evMachine));
                    if (evSupplier != null) batchSupplierLinks.add(Map.of("bId", evBatch, "sId", evSupplier));
                    if (evProduct != null) batchProductLinks.add(Map.of("bId", evBatch, "pId", evProduct));
                    if (evCustomer != null) batchCustomerLinks.add(Map.of("bId", evBatch, "cId", evCustomer));
                    if (evWarehouse != null) batchWarehouseLinks.add(Map.of("bId", evBatch, "wId", evWarehouse));
                }
            }

            // Explicit investigation links
            for (InvestigationEvidence link : explicitLinks) {
                if (link.getInvestigation() == null || link.getInvestigation().getId() == null
                        || link.getCanonicalEvidence() == null || link.getCanonicalEvidence().getExternalId() == null
                        || Boolean.TRUE.equals(link.getCanonicalEvidence().getIsDeleted())) continue;
                Long linkOrgId = link.getOrganisation() != null ? link.getOrganisation().getOrgId() : null;
                Long evidenceOrgId = link.getCanonicalEvidence().getOrganisation() != null
                        ? link.getCanonicalEvidence().getOrganisation().getOrgId() : null;
                if ((linkOrgId != null && !orgId.equals(linkOrgId)) || (evidenceOrgId != null && !orgId.equals(evidenceOrgId))) continue;
                incidentLinks.add(Map.of("incidentId", String.valueOf(link.getInvestigation().getId()), "externalId", link.getCanonicalEvidence().getExternalId().trim()));
            }

            // Execute all projections in batch UNWIND statements inside a single transaction
            session.executeWrite(tx -> {
                if (!evidenceItems.isEmpty()) {
                    tx.run("""
                            UNWIND $batch AS item
                            MERGE (e:Evidence {orgId: $orgId, stableId: item.stableId})
                            SET e.title = item.title, e.sourceType = item.sourceType, e.status = item.status,
                                e.sourceCreatedAt = item.sourceCreatedAt, e.sourceUpdatedAt = item.sourceUpdatedAt
                            """, Map.of("orgId", orgId, "batch", evidenceItems));
                }
                if (!caseItems.isEmpty()) {
                    tx.run("""
                            UNWIND $batch AS item
                            MERGE (c:Case {orgId: $orgId, stableId: item.caseId})
                            WITH c, item
                            MATCH (e:Evidence {orgId: $orgId, stableId: item.externalId})
                            MERGE (e)-[:BELONGS_TO]->(c)
                            """, Map.of("orgId", orgId, "batch", caseItems));
                }
                if (!actorItems.isEmpty()) {
                    tx.run("""
                            UNWIND $batch AS item
                            MERGE (a:Actor {orgId: $orgId, stableId: item.actorId})
                            WITH a, item
                            MATCH (e:Evidence {orgId: $orgId, stableId: item.externalId})
                            MERGE (e)-[:CREATED_BY]->(a)
                            """, Map.of("orgId", orgId, "batch", actorItems));
                }
                if (!parentLinks.isEmpty()) {
                    tx.run("""
                            UNWIND $batch AS item
                            MERGE (p:Evidence {orgId: $orgId, stableId: item.parentId})
                            WITH p, item
                            MATCH (e:Evidence {orgId: $orgId, stableId: item.externalId})
                            MERGE (e)-[:DERIVED_FROM]->(p)
                            """, Map.of("orgId", orgId, "batch", parentLinks));
                }
                if (!batchLinks.isEmpty()) {
                    tx.run("""
                            UNWIND $batch AS item
                            MERGE (b:Batch {orgId: $orgId, stableId: item.id})
                            SET b.title = item.id, b.name = item.id
                            WITH b, item
                            MATCH (e:Evidence {orgId: $orgId, stableId: item.externalId})
                            MERGE (e)-[:REFERENCES]->(b)
                            """, Map.of("orgId", orgId, "batch", batchLinks));
                }
                if (!machineLinks.isEmpty()) {
                    tx.run("""
                            UNWIND $batch AS item
                            MERGE (m:Machine {orgId: $orgId, stableId: item.id})
                            SET m.title = item.id, m.name = item.id
                            WITH m, item
                            MATCH (e:Evidence {orgId: $orgId, stableId: item.externalId})
                            MERGE (e)-[:REFERENCES]->(m)
                            """, Map.of("orgId", orgId, "batch", machineLinks));
                }
                if (!supplierLinks.isEmpty()) {
                    tx.run("""
                            UNWIND $batch AS item
                            MERGE (s:Supplier {orgId: $orgId, stableId: item.id})
                            SET s.title = item.id, s.name = item.id
                            WITH s, item
                            MATCH (e:Evidence {orgId: $orgId, stableId: item.externalId})
                            MERGE (e)-[:REFERENCES]->(s)
                            """, Map.of("orgId", orgId, "batch", supplierLinks));
                }
                if (!productLinks.isEmpty()) {
                    tx.run("""
                            UNWIND $batch AS item
                            MERGE (p:Product {orgId: $orgId, stableId: item.id})
                            SET p.title = item.id, p.name = item.id
                            WITH p, item
                            MATCH (e:Evidence {orgId: $orgId, stableId: item.externalId})
                            MERGE (e)-[:REFERENCES]->(p)
                            """, Map.of("orgId", orgId, "batch", productLinks));
                }
                if (!customerLinks.isEmpty()) {
                    tx.run("""
                            UNWIND $batch AS item
                            MERGE (c:Customer {orgId: $orgId, stableId: item.id})
                            SET c.title = item.id, c.name = item.id
                            WITH c, item
                            MATCH (e:Evidence {orgId: $orgId, stableId: item.externalId})
                            MERGE (e)-[:REFERENCES]->(c)
                            """, Map.of("orgId", orgId, "batch", customerLinks));
                }
                if (!warehouseLinks.isEmpty()) {
                    tx.run("""
                            UNWIND $batch AS item
                            MERGE (w:Warehouse {orgId: $orgId, stableId: item.id})
                            SET w.title = item.id, w.name = item.id
                            WITH w, item
                            MATCH (e:Evidence {orgId: $orgId, stableId: item.externalId})
                            MERGE (e)-[:REFERENCES]->(w)
                            """, Map.of("orgId", orgId, "batch", warehouseLinks));
                }
                if (!batchMachineLinks.isEmpty()) {
                    tx.run("""
                            UNWIND $batch AS item
                            MATCH (b:Batch {orgId: $orgId, stableId: item.bId})
                            MATCH (m:Machine {orgId: $orgId, stableId: item.mId})
                            MERGE (b)-[:ASSOCIATED_WITH]->(m)
                            """, Map.of("orgId", orgId, "batch", batchMachineLinks));
                }
                if (!batchSupplierLinks.isEmpty()) {
                    tx.run("""
                            UNWIND $batch AS item
                            MATCH (b:Batch {orgId: $orgId, stableId: item.bId})
                            MATCH (s:Supplier {orgId: $orgId, stableId: item.sId})
                            MERGE (b)-[:ASSOCIATED_WITH]->(s)
                            """, Map.of("orgId", orgId, "batch", batchSupplierLinks));
                }
                if (!batchProductLinks.isEmpty()) {
                    tx.run("""
                            UNWIND $batch AS item
                            MATCH (b:Batch {orgId: $orgId, stableId: item.bId})
                            MATCH (p:Product {orgId: $orgId, stableId: item.pId})
                            MERGE (b)-[:ASSOCIATED_WITH]->(p)
                            """, Map.of("orgId", orgId, "batch", batchProductLinks));
                }
                if (!batchCustomerLinks.isEmpty()) {
                    tx.run("""
                            UNWIND $batch AS item
                            MATCH (b:Batch {orgId: $orgId, stableId: item.bId})
                            MATCH (c:Customer {orgId: $orgId, stableId: item.cId})
                            MERGE (b)-[:ASSOCIATED_WITH]->(c)
                            """, Map.of("orgId", orgId, "batch", batchCustomerLinks));
                }
                if (!batchWarehouseLinks.isEmpty()) {
                    tx.run("""
                            UNWIND $batch AS item
                            MATCH (b:Batch {orgId: $orgId, stableId: item.bId})
                            MATCH (w:Warehouse {orgId: $orgId, stableId: item.wId})
                            MERGE (b)-[:ASSOCIATED_WITH]->(w)
                            """, Map.of("orgId", orgId, "batch", batchWarehouseLinks));
                }
                if (!incidentLinks.isEmpty()) {
                    tx.run("""
                            UNWIND $batch AS item
                            MATCH (i:Incident {orgId: $orgId, stableId: item.incidentId})
                            MATCH (e:Evidence {orgId: $orgId, stableId: item.externalId})
                            MERGE (i)-[:HAS_EVIDENCE]->(e)
                            """, Map.of("orgId", orgId, "batch", incidentLinks));
                }
                return null;
            });
            relationships += (caseItems.size() + actorItems.size() + parentLinks.size() + batchLinks.size() +
                    machineLinks.size() + supplierLinks.size() + productLinks.size() + customerLinks.size() +
                    warehouseLinks.size() + batchMachineLinks.size() + batchSupplierLinks.size() +
                    batchProductLinks.size() + batchCustomerLinks.size() + batchWarehouseLinks.size() + incidentLinks.size());
        }

        return GraphProjectionResult.builder()
                .organisationId(orgId)
                .evidenceProjected(active.size())
                .casesProjected(seenCases.size())
                .actorsProjected(seenActors.size())
                .incidentsProjected(seenIncidents.size())
                .relationshipsProjected(relationships)
                .skipped(skipped)
                .build();
    }

    private String extractString(Map<String, Object> map, String... keys) {
        if (map == null) return null;
        for (String k : keys) {
            Object val = map.get(k);
            if (val != null) {
                String str = val.toString().trim();
                if (!str.isEmpty() && !"null".equalsIgnoreCase(str)) {
                    return str;
                }
            }
        }
        return null;
    }
}
