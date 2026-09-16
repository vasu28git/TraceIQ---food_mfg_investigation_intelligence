package com.taceiq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taceiq.dto.FindingEvidenceTraceabilityResponse;
import com.taceiq.dto.InvestigationFindingResponse;
import com.taceiq.entity.*;
import com.taceiq.graph.GraphQueryRepository;
import com.taceiq.graph.config.Neo4jConfig;
import com.taceiq.graph.dto.GraphProjectionResult;
import com.taceiq.graph.dto.GraphValidationResult;
import com.taceiq.graph.service.*;
import com.taceiq.ingestion.SourceFieldMapper;
import com.taceiq.ingestion.SourceRecordIngestionService;
import com.taceiq.repository.*;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.FindingEvidenceTraceabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class OrganisationGraphIsolationTest {

    private CanonicalEvidenceRepository canonicalRepo;
    private Driver driver;
    private Session session;
    private TransactionContext tx;
    private Neo4jConfig config;
    private InvestigationRepository investigationRepository;
    private InvestigationEvidenceRepository linkRepository;
    private GraphProjectionService projectionService;

    private Organisation orgA = Organisation.builder().orgId(1L).name("OrgA").build();
    private Organisation orgB = Organisation.builder().orgId(2L).name("OrgB").build();

    @BeforeEach
    void setup() {
        canonicalRepo = mock(CanonicalEvidenceRepository.class);
        driver = mock(Driver.class);
        session = mock(Session.class);
        tx = mock(TransactionContext.class);
        config = mock(Neo4jConfig.class);
        investigationRepository = mock(InvestigationRepository.class);
        linkRepository = mock(InvestigationEvidenceRepository.class);

        when(config.getDatabase()).thenReturn("neo4j");
        when(config.isConfigured()).thenReturn(true);
        when(driver.session(any(SessionConfig.class))).thenReturn(session);
        when(session.executeWrite(any(org.neo4j.driver.TransactionCallback.class))).thenAnswer(inv -> {
            org.neo4j.driver.TransactionCallback<?> work = inv.getArgument(0);
            return work.execute(tx);
        });
        when(tx.run(anyString(), anyMap())).thenReturn(mock(org.neo4j.driver.Result.class));

        projectionService = new GraphProjectionService(canonicalRepo, driver, config, investigationRepository, linkRepository);
    }

    private CanonicalEvidence buildEvidence(Long orgId, String externalId, String batch, String machine) {
        Organisation org = Organisation.builder().orgId(orgId).build();
        Map<String, Object> norm = new LinkedHashMap<>();
        norm.put("batchReference", batch);
        norm.put("machineReference", machine);
        String payload = "{}";
        try {
            payload = new ObjectMapper().writeValueAsString(norm);
        } catch (Exception ignored) {}

        return CanonicalEvidence.builder()
                .id(Math.abs((long) externalId.hashCode()))
                .organisation(org)
                .externalId(externalId)
                .title("Title " + externalId)
                .sourceType("MES")
                .status("READY")
                .contentHash("hash_" + externalId)
                .normalizedPayload(payload)
                .firstSeenAt(Instant.now())
                .lastSeenAt(Instant.now())
                .isDeleted(false)
                .build();
    }

    // =========================================================================
    // TEST 1 — Separate graph data
    // Create Org A and Org B. Upload/create evidence for both. Project both.
    // Verify: Org A graph contains only Org A entities. Org B contains only Org B.
    // =========================================================================
    @Test
    void test1_separateGraphData() {
        when(canonicalRepo.findAllByOrganisationOrgId(1L))
                .thenReturn(List.of(buildEvidence(1L, "EV_A1", "BATCH-A", "M-A")));
        when(canonicalRepo.findAllByOrganisationOrgId(2L))
                .thenReturn(List.of(buildEvidence(2L, "EV_B1", "BATCH-B", "M-B")));

        // Project Org A
        GraphProjectionResult resA = projectionService.projectForOrganisation(1L);
        assertEquals(1, resA.getEvidenceProjected());

        ArgumentCaptor<Map> paramsCaptorA = ArgumentCaptor.forClass(Map.class);
        verify(tx, atLeastOnce()).run(anyString(), paramsCaptorA.capture());

        // Verify all projected nodes/relationships in Org A projection strictly use orgId = 1L
        for (Map map : paramsCaptorA.getAllValues()) {
            if (map.containsKey("orgId")) {
                assertEquals(1L, map.get("orgId"), "Org A projection must strictly use orgId 1L");
            }
        }

        // Reset tx mock for Org B
        reset(tx);
        when(tx.run(anyString(), anyMap())).thenReturn(mock(org.neo4j.driver.Result.class));

        // Project Org B
        GraphProjectionResult resB = projectionService.projectForOrganisation(2L);
        assertEquals(1, resB.getEvidenceProjected());

        ArgumentCaptor<Map> paramsCaptorB = ArgumentCaptor.forClass(Map.class);
        verify(tx, atLeastOnce()).run(anyString(), paramsCaptorB.capture());

        for (Map map : paramsCaptorB.getAllValues()) {
            if (map.containsKey("orgId")) {
                assertEquals(2L, map.get("orgId"), "Org B projection must strictly use orgId 2L");
            }
        }
    }

    // =========================================================================
    // TEST 2 — Same entity name across organisations
    // Org A: Batch = BATCH-101
    // Org B: Batch = BATCH-101
    // Verify these are two independent graph entities. They must NOT merge.
    // =========================================================================
    @Test
    void test2_sameEntityNameAcrossOrganisations_independentNodes() {
        when(canonicalRepo.findAllByOrganisationOrgId(1L))
                .thenReturn(List.of(buildEvidence(1L, "EV_A", "BATCH-101", null)));
        when(canonicalRepo.findAllByOrganisationOrgId(2L))
                .thenReturn(List.of(buildEvidence(2L, "EV_B", "BATCH-101", null)));

        // Project Org A
        projectionService.projectForOrganisation(1L);
        ArgumentCaptor<String> queryA = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> paramsA = ArgumentCaptor.forClass(Map.class);
        verify(tx, atLeastOnce()).run(queryA.capture(), paramsA.capture());

        boolean batchAProjectedWithOrg1 = false;
        for (int i = 0; i < queryA.getAllValues().size(); i++) {
            String q = queryA.getAllValues().get(i);
            Map p = paramsA.getAllValues().get(i);
            if (q.contains("MERGE (b:Batch {orgId: $orgId, stableId: $id})") && "BATCH-101".equals(p.get("id"))) {
                assertEquals(1L, p.get("orgId"));
                batchAProjectedWithOrg1 = true;
            }
        }
        assertTrue(batchAProjectedWithOrg1, "Batch BATCH-101 for Org 1 must have orgId = 1L in MERGE identity");

        // Project Org B
        reset(tx);
        when(tx.run(anyString(), anyMap())).thenReturn(mock(org.neo4j.driver.Result.class));
        projectionService.projectForOrganisation(2L);

        ArgumentCaptor<String> queryB = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> paramsB = ArgumentCaptor.forClass(Map.class);
        verify(tx, atLeastOnce()).run(queryB.capture(), paramsB.capture());

        boolean batchBProjectedWithOrg2 = false;
        for (int i = 0; i < queryB.getAllValues().size(); i++) {
            String q = queryB.getAllValues().get(i);
            Map p = paramsB.getAllValues().get(i);
            if (q.contains("MERGE (b:Batch {orgId: $orgId, stableId: $id})") && "BATCH-101".equals(p.get("id"))) {
                assertEquals(2L, p.get("orgId"));
                batchBProjectedWithOrg2 = true;
            }
        }
        assertTrue(batchBProjectedWithOrg2, "Batch BATCH-101 for Org 2 must have orgId = 2L in MERGE identity");
    }

    // =========================================================================
    // TEST 3 — Cross-tenant query attack
    // Authenticate as Org A. Attempt to query Org B's incident/evidence/graph.
    // Expected: 403/404. No Org B graph data should be returned.
    // =========================================================================
    @Test
    void test3_crossTenantQueryAttack_rejected() {
        AuthorizationService auth = mock(AuthorizationService.class);
        when(auth.getCurrentOrgId()).thenReturn(1L); // Authenticated as Org 1

        GraphReadinessService readiness = mock(GraphReadinessService.class);
        when(readiness.isOrgGraphReady(1L)).thenReturn(true);

        GraphQueryRepository queryRepo = mock(GraphQueryRepository.class);
        InvestigationRepository invRepo = mock(InvestigationRepository.class);

        // Incident 99 belongs to Org 2
        when(invRepo.findByIdAndOrganisationOrgId(99L, 1L)).thenReturn(Optional.empty());
        when(invRepo.findById(99L)).thenReturn(Optional.of(Investigation.builder().id(99L).organisation(orgB).build()));

        GraphQueryService queryService = new GraphQueryService(
                queryRepo, readiness, auth, mock(ConfigurationRepository.class),
                mock(ConfigurationDefinitionRepository.class), invRepo, canonicalRepo
        );

        // Org 1 tries to trace Org 2's incident 99
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> queryService.traceIncident(99L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(queryRepo, never()).traceIncident(eq(2L), anyLong(), anyInt());

        // Cross-tenant evidence lookup in GraphWorkspaceService
        CanonicalEvidenceRepository canRepo = mock(CanonicalEvidenceRepository.class);
        when(canRepo.findByExternalIdAndOrganisationOrgId("EV_B_PRIVATE", 1L)).thenReturn(Optional.empty());
        when(canRepo.findByExternalIdInAndOrganisationOrgId(List.of("EV_B_PRIVATE"), 1L)).thenReturn(List.of());

        GraphWorkspaceService workspaceService = new GraphWorkspaceService(
                queryRepo, canRepo, mock(IngestedSourceRecordRepository.class),
                readiness, auth, new ObjectMapper()
        );

        ResponseStatusException evEx = assertThrows(ResponseStatusException.class,
                () -> workspaceService.getEvidenceDetail("EV_B_PRIVATE"));
        assertEquals(HttpStatus.NOT_FOUND, evEx.getStatusCode());
    }

    // =========================================================================
    // TEST 4 — Cross-tenant relationship protection
    // Attempt relationship between Org A node and Org B node.
    // Verified preventive isolation in Cypher query AND detection in GraphValidator.
    // =========================================================================
    @Test
    void test4_crossTenantRelationshipProtection() {
        // 1. Preventive: Verify all relationship queries in GraphProjectionService enforce {orgId: $orgId} on both endpoints
        when(canonicalRepo.findAllByOrganisationOrgId(1L))
                .thenReturn(List.of(buildEvidence(1L, "EV_A1", "BATCH-A", "M-A")));

        projectionService.projectForOrganisation(1L);

        ArgumentCaptor<String> cypherCaptor = ArgumentCaptor.forClass(String.class);
        verify(tx, atLeastOnce()).run(cypherCaptor.capture(), anyMap());

        for (String cypher : cypherCaptor.getAllValues()) {
            if (cypher.contains("-[:REFERENCES]->") || cypher.contains("-[:ASSOCIATED_WITH]->") || cypher.contains("-[:HAS_EVIDENCE]->")) {
                // Both MATCH clauses must have {orgId: $orgId}
                assertTrue(cypher.contains("{orgId: $orgId"), "Relationship Cypher must enforce {orgId: $orgId} on endpoints: " + cypher);
            }
        }

        // 2. Detective: GraphValidator detects any cross-tenant edge
        GraphValidator validator = new GraphValidator(driver, config, canonicalRepo);
        Result crossResult = mock(Result.class);
        Record crossRec = mock(Record.class);
        when(crossRec.get("c")).thenReturn(Values.value(3L)); // 3 cross-org relationships detected
        when(crossResult.single()).thenReturn(crossRec);

        Result evResult = mock(Result.class);
        when(evResult.list()).thenReturn(List.of());

        Result dupResult = mock(Result.class);
        when(dupResult.list()).thenReturn(List.of());

        when(session.run(contains("MATCH (e:Evidence {orgId: $orgId})"), anyMap())).thenReturn(evResult);
        when(session.run(contains("WITH e.stableId"), anyMap())).thenReturn(dupResult);
        when(session.run(contains("MATCH (a)-[r]->(b)"), anyMap())).thenReturn(crossResult);

        GraphValidationResult valRes = validator.validate(1L);
        assertFalse(valRes.isValid(), "Graph validation must fail when cross-organisation relationships exist");
        assertTrue(valRes.getErrors().stream().anyMatch(e -> e.contains("Cross-organisation relationships")));
    }

    // =========================================================================
    // TEST 5 — Organisation-specific rebuild
    // Rebuilding Org A deletes & rebuilds Org A graph; Org B remains untouched.
    // =========================================================================
    @Test
    void test5_organisationSpecificRebuild_preservesOtherOrg() {
        when(canonicalRepo.findAllByOrganisationOrgId(1L))
                .thenReturn(List.of(buildEvidence(1L, "EV_A1", "BATCH-A", "M-A")));

        GraphProjectionResult result = projectionService.rebuildForOrganisation(1L);
        assertNotNull(result);
        assertEquals(1, result.getEvidenceProjected());

        // Verify DETACH DELETE was called strictly with orgId = 1L
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(tx, atLeastOnce()).run(queryCaptor.capture(), paramsCaptor.capture());

        boolean purgeMatchedOrg1Only = false;
        for (int i = 0; i < queryCaptor.getAllValues().size(); i++) {
            String q = queryCaptor.getAllValues().get(i);
            Map p = paramsCaptor.getAllValues().get(i);
            if (q.contains("MATCH (n {orgId: $orgId}) DETACH DELETE n")) {
                assertEquals(1L, p.get("orgId"), "Rebuild purge must ONLY target orgId = 1L");
                purgeMatchedOrg1Only = true;
            }
        }
        assertTrue(purgeMatchedOrg1Only, "Rebuild must execute tenant-scoped purge query");
    }

    // =========================================================================
    // TEST 6 — Incremental file upload
    // Org A uploads File 1. Verify Graph A.
    // Org A uploads File 2. Verify Graph A now contains knowledge from both files.
    // =========================================================================
    @Test
    void test6_incrementalFileUpload_extendsPersistentGraph() {
        // File 1: Production record
        CanonicalEvidence ev1 = buildEvidence(1L, "SRC_MES_PROD_101", "BATCH-101", "M-05");
        // File 2: Maintenance record
        CanonicalEvidence ev2 = buildEvidence(1L, "SRC_CMMS_MAINT_201", null, "M-05");

        // After File 1
        when(canonicalRepo.findAllByOrganisationOrgId(1L)).thenReturn(List.of(ev1));
        GraphProjectionResult res1 = projectionService.projectForOrganisation(1L);
        assertEquals(1, res1.getEvidenceProjected());

        // After File 2: Persistent canonical repo now has both ev1 and ev2
        when(canonicalRepo.findAllByOrganisationOrgId(1L)).thenReturn(List.of(ev1, ev2));
        reset(tx);
        when(tx.run(anyString(), anyMap())).thenReturn(mock(org.neo4j.driver.Result.class));

        GraphProjectionResult res2 = projectionService.projectForOrganisation(1L);
        assertEquals(2, res2.getEvidenceProjected(), "Graph must now contain both File 1 and File 2 evidence");

        ArgumentCaptor<Map> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(tx, atLeastOnce()).run(anyString(), paramsCaptor.capture());

        Set<String> projectedEvs = new HashSet<>();
        for (Map p : paramsCaptor.getAllValues()) {
            if (p.containsKey("stableId")) {
                projectedEvs.add((String) p.get("stableId"));
            }
        }
        assertTrue(projectedEvs.contains("SRC_MES_PROD_101"));
        assertTrue(projectedEvs.contains("SRC_CMMS_MAINT_201"));
    }

    // =========================================================================
    // TEST 7 — Finding traceability
    // Finding in Org A can only trace Org A evidence, never Org B.
    // =========================================================================
    @Test
    void test7_findingTraceability_strictlyTenantScoped() {
        AuthorizationService auth = mock(AuthorizationService.class);
        when(auth.getCurrentOrgId()).thenReturn(1L); // Org 1

        InvestigationRepository invRepo = mock(InvestigationRepository.class);
        InvestigationFindingRepository findRepo = mock(InvestigationFindingRepository.class);
        InvestigationFindingEvidenceRepository feRepo = mock(InvestigationFindingEvidenceRepository.class);
        EvidenceCorrelationProvenanceRepository provRepo = mock(EvidenceCorrelationProvenanceRepository.class);
        IngestedSourceRecordRepository srcRepo = mock(IngestedSourceRecordRepository.class);

        Investigation invA = Investigation.builder().id(10L).organisation(orgA).build();
        InvestigationFinding findA = InvestigationFinding.builder().id(101L).organisation(orgA).investigation(invA).build();
        CanonicalEvidence evA = buildEvidence(1L, "EV_A1", "BATCH-1", null);
        InvestigationFindingEvidence linkA = InvestigationFindingEvidence.builder()
                .id(1L).organisation(orgA).investigation(invA).finding(findA).canonicalEvidence(evA).relationshipType("SUPPORTING")
                .build();

        when(invRepo.findByIdAndOrganisationOrgId(10L, 1L)).thenReturn(Optional.of(invA));
        when(findRepo.findByIdAndOrganisationOrgIdAndInvestigationId(101L, 1L, 10L)).thenReturn(Optional.of(findA));
        when(feRepo.findByOrganisationOrgIdAndInvestigationIdAndFindingId(1L, 10L, 101L)).thenReturn(List.of(linkA));
        when(provRepo.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(1L, 10L, evA.getId())).thenReturn(List.of());

        FindingEvidenceTraceabilityService traceabilityService = new FindingEvidenceTraceabilityService(
                invRepo, findRepo, feRepo, provRepo, srcRepo, auth, new ObjectMapper()
        );

        // Org 1 traces its own finding
        FindingEvidenceTraceabilityResponse resp = traceabilityService.trace(10L, 101L);
        assertNotNull(resp);
        assertEquals(1, resp.getEvidence().size());
        assertEquals("EV_A1", resp.getEvidence().get(0).getEvidenceId());

        // Org 1 tries to trace Org 2 investigation 20
        when(invRepo.findByIdAndOrganisationOrgId(20L, 1L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> traceabilityService.trace(20L, 201L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // =========================================================================
    // TEST 8 — Duplicate processing
    // Process same file/evidence twice; verify graph entity counts do not unexpectedly increase.
    // =========================================================================
    @Test
    void test8_duplicateProcessing_idempotent() {
        FileRepository fileRepo = mock(FileRepository.class);
        IngestedSourceRecordRepository srcRepo = mock(IngestedSourceRecordRepository.class);
        OrganisationRepository orgRepo = mock(OrganisationRepository.class);
        CanonicalEvidenceRepository canRepo = mock(CanonicalEvidenceRepository.class);

        when(orgRepo.getReferenceById(1L)).thenReturn(orgA);
        File testFile = File.builder().id(5L).organisation(orgA).originalName("batch_test.csv").build();
        when(fileRepo.findById(5L)).thenReturn(Optional.of(testFile));

        // Create temporary test CSV file
        try {
            Path tempCsv = Files.createTempFile("taceiq_test", ".csv");
            Files.writeString(tempCsv, "record_id,batch_number,machine_id\nREC-001,BATCH-10,M-01\n");
            testFile.setStorageKey(tempCsv.toAbsolutePath().toString());

            SourceRecordIngestionService ingestionService = new SourceRecordIngestionService(
                    fileRepo, srcRepo, orgRepo, new SourceFieldMapper(), new ObjectMapper(), canRepo
            );

            // First run: new record
            when(srcRepo.findByOrganisationOrgIdAndSourceTypeAndSourceRecordId(1L, "MES", "REC-001"))
                    .thenReturn(Optional.empty());
            when(canRepo.findByExternalIdAndOrganisationOrgId(anyString(), eq(1L)))
                    .thenReturn(Optional.empty());

            SourceRecordIngestionService.IngestionResult res1 = ingestionService.ingestFile(1L, 5L, "MES");
            assertEquals(1, res1.created);
            assertEquals(0, res1.reused);

            // Second run: existing record (reused, idempotent)
            IngestedSourceRecord existing = IngestedSourceRecord.builder()
                    .id(1L).organisation(orgA).sourceType("MES").sourceRecordId("REC-001")
                    .batchReference("BATCH-10").machineReference("M-01")
                    .build();
            when(srcRepo.findByOrganisationOrgIdAndSourceTypeAndSourceRecordId(1L, "MES", "REC-001"))
                    .thenReturn(Optional.of(existing));
            CanonicalEvidence existingCan = buildEvidence(1L, "SRC_MES_REC-001", "BATCH-10", "M-01");
            when(canRepo.findByExternalIdAndOrganisationOrgId(anyString(), eq(1L)))
                    .thenReturn(Optional.of(existingCan));

            SourceRecordIngestionService.IngestionResult res2 = ingestionService.ingestFile(1L, 5L, "MES");
            assertEquals(0, res2.created, "No duplicate records created on second ingestion");
            assertEquals(1, res2.reused, "Existing record was reused");

            Files.deleteIfExists(tempCsv);
        } catch (Exception e) {
            fail("Test failed with exception: " + e.getMessage());
        }
    }
}
