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

            for (CanonicalEvidence ev : active) {
                String externalId = ev.getExternalId();
                if (externalId == null || externalId.isBlank()) {
                    skipped++;
                    continue;
                }
                // Evidence node
                session.executeWrite(tx -> {
                    Map<String, Object> params = Map.of(
                            "orgId", orgId,
                            "stableId", externalId.trim(),
                            "title", ev.getTitle() != null ? ev.getTitle() : "",
                            "sourceType", ev.getSourceType() != null ? ev.getSourceType() : "",
                            "status", ev.getStatus() != null ? ev.getStatus() : "",
                            "sourceCreatedAt", ev.getSourceCreatedAt() != null ? ev.getSourceCreatedAt().toString() : "",
                            "sourceUpdatedAt", ev.getSourceUpdatedAt() != null ? ev.getSourceUpdatedAt().toString() : ""
                    );
                    tx.run("""
                            MERGE (e:Evidence {orgId: $orgId, stableId: $stableId})
                            SET e.title = $title, e.sourceType = $sourceType, e.status = $status,
                                e.sourceCreatedAt = $sourceCreatedAt, e.sourceUpdatedAt = $sourceUpdatedAt
                            """, params);
                    return null;
                });

                // Incident -> Evidence HAS_EVIDENCE (Phase 3, tenant-safe, idempotent)
                if (ev.getIncident() != null && ev.getIncident().getId() != null) {
                    String incStable = String.valueOf(ev.getIncident().getId());
                    // Tenant isolation: incident org must match projection org
                    Long incOrgId = null;
                    try {
                        if (ev.getIncident().getOrganisation() != null) incOrgId = ev.getIncident().getOrganisation().getOrgId();
                    } catch (Exception ignored) {}
                    if (incOrgId == null || incOrgId.equals(orgId)) {
                        session.executeWrite(tx -> {
                            tx.run("""
                                    MATCH (i:Incident {orgId: $orgId, stableId: $incidentId})
                                    MATCH (e:Evidence {orgId: $orgId, stableId: $externalId})
                                    MERGE (i)-[:HAS_EVIDENCE]->(e)
                                    """, Map.of("orgId", orgId, "incidentId", incStable, "externalId", externalId.trim()));
                            return null;
                        });
                        relationships++;
                    }
                }

                // Case node + BELONGS_TO
                String caseId = ev.getCaseId();
                if (caseId != null && !caseId.isBlank()) {
                    String cId = caseId.trim();
                    seenCases.add(cId);
                    session.executeWrite(tx -> {
                        tx.run("MERGE (c:Case {orgId: $orgId, stableId: $caseId})",
                                Map.of("orgId", orgId, "caseId", cId));
                        tx.run("""
                                MATCH (e:Evidence {orgId: $orgId, stableId: $externalId})
                                MATCH (c:Case {orgId: $orgId, stableId: $caseId})
                                MERGE (e)-[:BELONGS_TO]->(c)
                                """, Map.of("orgId", orgId, "externalId", externalId.trim(), "caseId", cId));
                        return null;
                    });
                    relationships++;
                }

                // Actor node + CREATED_BY
                String actorId = ev.getActorId();
                if (actorId != null && !actorId.isBlank()) {
                    String aId = actorId.trim();
                    seenActors.add(aId);
                    session.executeWrite(tx -> {
                        tx.run("MERGE (a:Actor {orgId: $orgId, stableId: $actorId})",
                                Map.of("orgId", orgId, "actorId", aId));
                        tx.run("""
                                MATCH (e:Evidence {orgId: $orgId, stableId: $externalId})
                                MATCH (a:Actor {orgId: $orgId, stableId: $actorId})
                                MERGE (e)-[:CREATED_BY]->(a)
                                """, Map.of("orgId", orgId, "externalId", externalId.trim(), "actorId", aId));
                        return null;
                    });
                    relationships++;
                }

                // Parent Evidence DERIVED_FROM
                String parentId = ev.getParentId();
                if (parentId != null && !parentId.isBlank()) {
                    String pId = parentId.trim();
                    // Only create if parentId not blank and not self
                    if (!pId.equals(externalId.trim())) {
                        session.executeWrite(tx -> {
                            // Ensure parent node exists (may not yet be canonical, but MERGE creates placeholder)
                            tx.run("MERGE (p:Evidence {orgId: $orgId, stableId: $parentId})",
                                    Map.of("orgId", orgId, "parentId", pId));
                            tx.run("""
                                    MATCH (e:Evidence {orgId: $orgId, stableId: $externalId})
                                    MATCH (p:Evidence {orgId: $orgId, stableId: $parentId})
                                    MERGE (e)-[:DERIVED_FROM]->(p)
                                    """, Map.of("orgId", orgId, "externalId", externalId.trim(), "parentId", pId));
                            return null;
                        });
                        relationships++;
                    }
                }

                // Entity node projection and multi-hop relationships (REFERENCES, ASSOCIATED_WITH)
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

                final String fEvBatch = evBatch;
                if (fEvBatch != null) {
                    session.executeWrite(tx -> {
                        tx.run("MERGE (b:Batch {orgId: $orgId, stableId: $id}) SET b.title = $id, b.name = $id", Map.of("orgId", orgId, "id", fEvBatch));
                        tx.run("""
                                MATCH (e:Evidence {orgId: $orgId, stableId: $externalId})
                                MATCH (b:Batch {orgId: $orgId, stableId: $id})
                                MERGE (e)-[:REFERENCES]->(b)
                                """, Map.of("orgId", orgId, "externalId", externalId.trim(), "id", fEvBatch));
                        return null;
                    });
                    relationships++;
                }

                final String fEvMachine = evMachine;
                if (fEvMachine != null) {
                    session.executeWrite(tx -> {
                        tx.run("MERGE (m:Machine {orgId: $orgId, stableId: $id}) SET m.title = $id, m.name = $id", Map.of("orgId", orgId, "id", fEvMachine));
                        tx.run("""
                                MATCH (e:Evidence {orgId: $orgId, stableId: $externalId})
                                MATCH (m:Machine {orgId: $orgId, stableId: $id})
                                MERGE (e)-[:REFERENCES]->(m)
                                """, Map.of("orgId", orgId, "externalId", externalId.trim(), "id", fEvMachine));
                        return null;
                    });
                    relationships++;
                }

                final String fEvSupplier = evSupplier;
                if (fEvSupplier != null) {
                    session.executeWrite(tx -> {
                        tx.run("MERGE (s:Supplier {orgId: $orgId, stableId: $id}) SET s.title = $id, s.name = $id", Map.of("orgId", orgId, "id", fEvSupplier));
                        tx.run("""
                                MATCH (e:Evidence {orgId: $orgId, stableId: $externalId})
                                MATCH (s:Supplier {orgId: $orgId, stableId: $id})
                                MERGE (e)-[:REFERENCES]->(s)
                                """, Map.of("orgId", orgId, "externalId", externalId.trim(), "id", fEvSupplier));
                        return null;
                    });
                    relationships++;
                }

                final String fEvProduct = evProduct;
                if (fEvProduct != null) {
                    session.executeWrite(tx -> {
                        tx.run("MERGE (p:Product {orgId: $orgId, stableId: $id}) SET p.title = $id, p.name = $id", Map.of("orgId", orgId, "id", fEvProduct));
                        tx.run("""
                                MATCH (e:Evidence {orgId: $orgId, stableId: $externalId})
                                MATCH (p:Product {orgId: $orgId, stableId: $id})
                                MERGE (e)-[:REFERENCES]->(p)
                                """, Map.of("orgId", orgId, "externalId", externalId.trim(), "id", fEvProduct));
                        return null;
                    });
                    relationships++;
                }

                final String fEvCustomer = evCustomer;
                if (fEvCustomer != null) {
                    session.executeWrite(tx -> {
                        tx.run("MERGE (c:Customer {orgId: $orgId, stableId: $id}) SET c.title = $id, c.name = $id", Map.of("orgId", orgId, "id", fEvCustomer));
                        tx.run("""
                                MATCH (e:Evidence {orgId: $orgId, stableId: $externalId})
                                MATCH (c:Customer {orgId: $orgId, stableId: $id})
                                MERGE (e)-[:REFERENCES]->(c)
                                """, Map.of("orgId", orgId, "externalId", externalId.trim(), "id", fEvCustomer));
                        return null;
                    });
                    relationships++;
                }

                final String fEvWarehouse = evWarehouse;
                if (fEvWarehouse != null) {
                    session.executeWrite(tx -> {
                        tx.run("MERGE (w:Warehouse {orgId: $orgId, stableId: $id}) SET w.title = $id, w.name = $id", Map.of("orgId", orgId, "id", fEvWarehouse));
                        tx.run("""
                                MATCH (e:Evidence {orgId: $orgId, stableId: $externalId})
                                MATCH (w:Warehouse {orgId: $orgId, stableId: $id})
                                MERGE (e)-[:REFERENCES]->(w)
                                """, Map.of("orgId", orgId, "externalId", externalId.trim(), "id", fEvWarehouse));
                        return null;
                    });
                    relationships++;
                }

                // Batch -> Entity ASSOCIATED_WITH
                if (fEvBatch != null) {
                    if (fEvMachine != null) {
                        session.executeWrite(tx -> {
                            tx.run("""
                                    MATCH (b:Batch {orgId: $orgId, stableId: $bId})
                                    MATCH (m:Machine {orgId: $orgId, stableId: $mId})
                                    MERGE (b)-[:ASSOCIATED_WITH]->(m)
                                    """, Map.of("orgId", orgId, "bId", fEvBatch, "mId", fEvMachine));
                            return null;
                        });
                        relationships++;
                    }
                    if (fEvSupplier != null) {
                        session.executeWrite(tx -> {
                            tx.run("""
                                    MATCH (b:Batch {orgId: $orgId, stableId: $bId})
                                    MATCH (s:Supplier {orgId: $orgId, stableId: $sId})
                                    MERGE (b)-[:ASSOCIATED_WITH]->(s)
                                    """, Map.of("orgId", orgId, "bId", fEvBatch, "sId", fEvSupplier));
                            return null;
                        });
                        relationships++;
                    }
                    if (fEvProduct != null) {
                        session.executeWrite(tx -> {
                            tx.run("""
                                    MATCH (b:Batch {orgId: $orgId, stableId: $bId})
                                    MATCH (p:Product {orgId: $orgId, stableId: $pId})
                                    MERGE (b)-[:ASSOCIATED_WITH]->(p)
                                    """, Map.of("orgId", orgId, "bId", fEvBatch, "pId", fEvProduct));
                            return null;
                        });
                        relationships++;
                    }
                    if (fEvCustomer != null) {
                        session.executeWrite(tx -> {
                            tx.run("""
                                    MATCH (b:Batch {orgId: $orgId, stableId: $bId})
                                    MATCH (c:Customer {orgId: $orgId, stableId: $cId})
                                    MERGE (b)-[:ASSOCIATED_WITH]->(c)
                                    """, Map.of("orgId", orgId, "bId", fEvBatch, "cId", fEvCustomer));
                            return null;
                        });
                        relationships++;
                    }
                    if (fEvWarehouse != null) {
                        session.executeWrite(tx -> {
                            tx.run("""
                                    MATCH (b:Batch {orgId: $orgId, stableId: $bId})
                                    MATCH (w:Warehouse {orgId: $orgId, stableId: $wId})
                                    MERGE (b)-[:ASSOCIATED_WITH]->(w)
                                    """, Map.of("orgId", orgId, "bId", fEvBatch, "wId", fEvWarehouse));
                            return null;
                        });
                        relationships++;
                    }
                }
            }

            // Explicit investigation links are PostgreSQL associations, but also become graph edges.
            for (InvestigationEvidence link : explicitLinks) {
                if (link.getInvestigation() == null || link.getInvestigation().getId() == null
                        || link.getCanonicalEvidence() == null || link.getCanonicalEvidence().getExternalId() == null
                        || Boolean.TRUE.equals(link.getCanonicalEvidence().getIsDeleted())) continue;
                Long linkOrgId = link.getOrganisation() != null ? link.getOrganisation().getOrgId() : null;
                Long evidenceOrgId = link.getCanonicalEvidence().getOrganisation() != null
                        ? link.getCanonicalEvidence().getOrganisation().getOrgId() : null;
                if ((linkOrgId != null && !orgId.equals(linkOrgId)) || (evidenceOrgId != null && !orgId.equals(evidenceOrgId))) continue;
                session.executeWrite(tx -> {
                    tx.run("""
                            MATCH (i:Incident {orgId: $orgId, stableId: $incidentId})
                            MATCH (e:Evidence {orgId: $orgId, stableId: $externalId})
                            MERGE (i)-[:HAS_EVIDENCE]->(e)
                            """, Map.of("orgId", orgId, "incidentId", String.valueOf(link.getInvestigation().getId()), "externalId", link.getCanonicalEvidence().getExternalId().trim()));
                    return null;
                });
                relationships++;
            }
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
