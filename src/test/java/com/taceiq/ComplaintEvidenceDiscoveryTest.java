package com.taceiq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taceiq.dto.CreateComplaintRequest;
import com.taceiq.dto.ComplaintResponse;
import com.taceiq.entity.*;
import com.taceiq.graph.config.Neo4jConfig;
import com.taceiq.graph.dto.GraphProjectionResult;
import com.taceiq.graph.service.GraphProjectionService;
import com.taceiq.graph.service.GraphReadinessService;
import com.taceiq.ingestion.EvidenceDiscoveryService;
import com.taceiq.ingestion.SourceFieldMapper;
import com.taceiq.ingestion.SourceRecordIngestionService;
import com.taceiq.repository.*;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.ComplaintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionContext;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ComplaintEvidenceDiscoveryTest {

    private IngestedSourceRecordRepository sourceRepository;
    private CanonicalEvidenceRepository canonicalRepository;
    private InvestigationRepository investigationRepository;
    private OrganisationRepository organisationRepository;
    private InvestigationEvidenceRepository investigationEvidenceRepository;
    private EvidenceCorrelationProvenanceRepository provenanceRepository;
    private GraphProjectionService graphProjectionService;
    private SourceRecordIngestionService ingestionService;
    private EvidenceDiscoveryService discoveryService;

    private ComplaintRepository complaintRepository;
    private IntegrationRepository integrationRepository;
    private AuthorizationService authorizationService;
    private GraphReadinessService graphReadinessService;
    private ComplaintService complaintService;

    private Organisation org1;
    private User user1;
    private Investigation investigation1010;

    // Driver & Neo4j mocks
    private Driver neo4jDriver;
    private Session neo4jSession;
    private TransactionContext neo4jTx;
    private Neo4jConfig neo4jConfig;

    @BeforeEach
    void setUp() {
        sourceRepository = mock(IngestedSourceRecordRepository.class);
        canonicalRepository = mock(CanonicalEvidenceRepository.class);
        investigationRepository = mock(InvestigationRepository.class);
        organisationRepository = mock(OrganisationRepository.class);
        investigationEvidenceRepository = mock(InvestigationEvidenceRepository.class);
        provenanceRepository = mock(EvidenceCorrelationProvenanceRepository.class);
        ingestionService = mock(SourceRecordIngestionService.class);

        org1 = Organisation.builder().orgId(1L).name("Hutsan").build();
        user1 = User.builder().id(10L).username("admin").organisation(org1).build();

        investigation1010 = Investigation.builder()
                .id(1L)
                .organisation(org1)
                .investigationKey("INV-CMP-502")
                .title("Customer complaint for BATCH-1010")
                .batchReference("BATCH-1010")
                .status("DRAFT")
                .build();

        when(organisationRepository.getReferenceById(1L)).thenReturn(org1);
        when(investigationRepository.findByIdAndOrganisationOrgId(1L, 1L)).thenReturn(Optional.of(investigation1010));
        when(investigationRepository.findById(1L)).thenReturn(Optional.of(investigation1010));
        when(sourceRepository.countByOrganisationOrgId(1L)).thenReturn(43L);

        // Neo4j mocks
        neo4jDriver = mock(Driver.class);
        neo4jSession = mock(Session.class);
        neo4jTx = mock(TransactionContext.class);
        neo4jConfig = mock(Neo4jConfig.class);
        when(neo4jConfig.getDatabase()).thenReturn("neo4j");
        when(neo4jConfig.isConfigured()).thenReturn(true);
        when(neo4jDriver.session(any(SessionConfig.class))).thenReturn(neo4jSession);
        when(neo4jSession.executeWrite(any(org.neo4j.driver.TransactionCallback.class))).thenAnswer(inv -> {
            org.neo4j.driver.TransactionCallback<?> work = inv.getArgument(0);
            return work.execute(neo4jTx);
        });
        when(neo4jTx.run(anyString(), anyMap())).thenReturn(mock(org.neo4j.driver.Result.class));

        graphProjectionService = new GraphProjectionService(canonicalRepository, neo4jDriver, neo4jConfig, investigationRepository, investigationEvidenceRepository);

        discoveryService = new EvidenceDiscoveryService(
                investigationRepository, sourceRepository, canonicalRepository, organisationRepository,
                graphProjectionService, ingestionService, new SourceFieldMapper(), new ObjectMapper(),
                investigationEvidenceRepository, provenanceRepository);

        // ComplaintService setup
        complaintRepository = mock(ComplaintRepository.class);
        integrationRepository = mock(IntegrationRepository.class);
        authorizationService = mock(AuthorizationService.class);
        graphReadinessService = mock(GraphReadinessService.class);

        when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        when(authorizationService.getCurrentUser()).thenReturn(user1);
        when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(true);

        complaintService = new ComplaintService(
                complaintRepository, investigationRepository, integrationRepository, organisationRepository,
                authorizationService, graphReadinessService, discoveryService);
    }

    private IngestedSourceRecord record(String sourceType, String recordId, String payload,
                                        String batch, String machine, String supplier, String product) {
        return IngestedSourceRecord.builder()
                .id((long) Math.abs(recordId.hashCode()))
                .organisation(org1)
                .sourceType(sourceType)
                .sourceRecordId(recordId)
                .batchReference(batch)
                .machineReference(machine)
                .supplierReference(supplier)
                .productReference(product)
                .payload(payload)
                .ingestedAt(Instant.now())
                .build();
    }

    /**
     * Builds the complete set of 43 source records across the 8 uploaded files
     */
    private List<IngestedSourceRecord> build43SourceRecords() {
        List<IngestedSourceRecord> list = new ArrayList<>();

        // 1. MES_batches.csv (5 records: BATCH-1010, 1011, 1012, 1015, 1019)
        list.add(record("MES", "BATCH-1010", "{\"batch_id\":\"BATCH-1010\",\"machine_id\":\"M-05\",\"supplier_id\":\"SUP-09\",\"product_id\":\"PRD-106\"}", "BATCH-1010", "M-05", "SUP-09", "PRD-106"));
        list.add(record("MES", "BATCH-1011", "{\"batch_id\":\"BATCH-1011\",\"machine_id\":\"M-02\",\"supplier_id\":\"SUP-02\",\"product_id\":\"PRD-102\"}", "BATCH-1011", "M-02", "SUP-02", "PRD-102"));
        list.add(record("MES", "BATCH-1012", "{\"batch_id\":\"BATCH-1012\",\"machine_id\":\"M-03\",\"supplier_id\":\"SUP-05\",\"product_id\":\"PRD-104\"}", "BATCH-1012", "M-03", "SUP-05", "PRD-104"));
        list.add(record("MES", "BATCH-1015", "{\"batch_id\":\"BATCH-1015\",\"machine_id\":\"M-06\",\"supplier_id\":\"SUP-11\",\"product_id\":\"PRD-105\"}", "BATCH-1015", "M-06", "SUP-11", "PRD-105"));
        list.add(record("MES", "BATCH-1019", "{\"batch_id\":\"BATCH-1019\",\"machine_id\":\"M-07\",\"supplier_id\":\"SUP-12\",\"product_id\":\"PRD-106\"}", "BATCH-1019", "M-07", "SUP-12", "PRD-106"));

        // 2. LIMS_QA.csv (6 records: QA-2001 & QA-2002 for BATCH-1010, 4 others)
        list.add(record("LIMS", "QA-2001", "{\"sample_id\":\"QA-2001\",\"batch_id\":\"BATCH-1010\",\"status\":\"FAIL\"}", "BATCH-1010", null, null, null));
        list.add(record("LIMS", "QA-2002", "{\"sample_id\":\"QA-2002\",\"batch_id\":\"BATCH-1010\",\"status\":\"RETEST\"}", "BATCH-1010", null, null, null));
        list.add(record("LIMS", "QA-2003", "{\"sample_id\":\"QA-2003\",\"batch_id\":\"BATCH-1011\",\"status\":\"PASS\"}", "BATCH-1011", null, null, null));
        list.add(record("LIMS", "QA-2004", "{\"sample_id\":\"QA-2004\",\"batch_id\":\"BATCH-1012\",\"status\":\"PASS\"}", "BATCH-1012", null, null, null));
        list.add(record("LIMS", "QA-2005", "{\"sample_id\":\"QA-2005\",\"batch_id\":\"BATCH-1015\",\"status\":\"PASS\"}", "BATCH-1015", null, null, null));
        list.add(record("LIMS", "QA-2006", "{\"sample_id\":\"QA-2006\",\"batch_id\":\"BATCH-1019\",\"status\":\"PASS\"}", "BATCH-1019", null, null, null));

        // 3. PACKAGING_Inspections.csv (5 records: PKG-401 for BATCH-1010, 4 others)
        list.add(record("PACKAGING", "PKG-401", "{\"inspection_id\":\"PKG-401\",\"batch_id\":\"BATCH-1010\",\"result\":\"DEFECT\"}", "BATCH-1010", null, null, null));
        list.add(record("PACKAGING", "PKG-402", "{\"inspection_id\":\"PKG-402\",\"batch_id\":\"BATCH-1011\",\"result\":\"PASS\"}", "BATCH-1011", null, null, null));
        list.add(record("PACKAGING", "PKG-403", "{\"inspection_id\":\"PKG-403\",\"batch_id\":\"BATCH-1012\",\"result\":\"PASS\"}", "BATCH-1012", null, null, null));
        list.add(record("PACKAGING", "PKG-404", "{\"inspection_id\":\"PKG-404\",\"batch_id\":\"BATCH-1015\",\"result\":\"PASS\"}", "BATCH-1015", null, null, null));
        list.add(record("PACKAGING", "PKG-405", "{\"inspection_id\":\"PKG-405\",\"batch_id\":\"BATCH-1019\",\"result\":\"PASS\"}", "BATCH-1019", null, null, null));

        // 4. Shipments.csv (5 records: SHIP-2017 for BATCH-1010, 4 others)
        list.add(record("SHIPMENT", "SHIP-2017", "{\"shipment_id\":\"SHIP-2017\",\"batch_id\":\"BATCH-1010\",\"customer_id\":\"CUST-1096\"}", "BATCH-1010", null, null, null));
        list.add(record("SHIPMENT", "SHIP-2018", "{\"shipment_id\":\"SHIP-2018\",\"batch_id\":\"BATCH-1011\",\"customer_id\":\"CUST-2001\"}", "BATCH-1011", null, null, null));
        list.add(record("SHIPMENT", "SHIP-2019", "{\"shipment_id\":\"SHIP-2019\",\"batch_id\":\"BATCH-1012\",\"customer_id\":\"CUST-2002\"}", "BATCH-1012", null, null, null));
        list.add(record("SHIPMENT", "SHIP-2020", "{\"shipment_id\":\"SHIP-2020\",\"batch_id\":\"BATCH-1015\",\"customer_id\":\"CUST-2003\"}", "BATCH-1015", null, null, null));
        list.add(record("SHIPMENT", "SHIP-2021", "{\"shipment_id\":\"SHIP-2021\",\"batch_id\":\"BATCH-1019\",\"customer_id\":\"CUST-2004\"}", "BATCH-1019", null, null, null));

        // 5. Warehouse.csv (6 records: LOG-3023..3026 for BATCH-1010, 2 others)
        list.add(record("WAREHOUSE", "LOG-3023", "{\"log_id\":\"LOG-3023\",\"batch_id\":\"BATCH-1010\",\"warehouse_zone\":\"WZ-A1\"}", "BATCH-1010", null, null, null));
        list.add(record("WAREHOUSE", "LOG-3024", "{\"log_id\":\"LOG-3024\",\"batch_id\":\"BATCH-1010\",\"warehouse_zone\":\"WZ-A1\"}", "BATCH-1010", null, null, null));
        list.add(record("WAREHOUSE", "LOG-3025", "{\"log_id\":\"LOG-3025\",\"batch_id\":\"BATCH-1010\",\"warehouse_zone\":\"WZ-A1\"}", "BATCH-1010", null, null, null));
        list.add(record("WAREHOUSE", "LOG-3026", "{\"log_id\":\"LOG-3026\",\"batch_id\":\"BATCH-1010\",\"warehouse_zone\":\"WZ-A1\"}", "BATCH-1010", null, null, null));
        list.add(record("WAREHOUSE", "LOG-3027", "{\"log_id\":\"LOG-3027\",\"batch_id\":\"BATCH-1011\",\"warehouse_zone\":\"WZ-B2\"}", "BATCH-1011", null, null, null));
        list.add(record("WAREHOUSE", "LOG-3028", "{\"log_id\":\"LOG-3028\",\"batch_id\":\"BATCH-1012\",\"warehouse_zone\":\"WZ-C3\"}", "BATCH-1012", null, null, null));

        // 6. CMMS_maintenance.csv (5 records: MAINT-1014, MAINT-1020, MAINT-1021 for M-05, 2 others)
        list.add(record("CMMS", "MAINT-1014", "{\"work_order\":\"MAINT-1014\",\"machine_id\":\"M-05\",\"type\":\"CALIBRATION\"}", null, "M-05", null, null));
        list.add(record("CMMS", "MAINT-1020", "{\"work_order\":\"MAINT-1020\",\"machine_id\":\"M-05\",\"type\":\"REPAIR\"}", null, "M-05", null, null));
        list.add(record("CMMS", "MAINT-1021", "{\"work_order\":\"MAINT-1021\",\"machine_id\":\"M-05\",\"type\":\"OVERHAUL\"}", null, "M-05", null, null));
        list.add(record("CMMS", "MAINT-1022", "{\"work_order\":\"MAINT-1022\",\"machine_id\":\"M-02\",\"type\":\"ROUTINE\"}", null, "M-02", null, null));
        list.add(record("CMMS", "MAINT-1023", "{\"work_order\":\"MAINT-1023\",\"machine_id\":\"M-07\",\"type\":\"ROUTINE\"}", null, "M-07", null, null));

        // 7. SOP_Audit.csv (8 records: DOC-301, 302, 303, 330 for M-05, 4 others)
        list.add(record("SOP", "DOC-301", "{\"sop_id\":\"DOC-301\",\"machine_id\":\"M-05\",\"version\":\"v2.1\"}", null, "M-05", null, null));
        list.add(record("SOP", "DOC-302", "{\"sop_id\":\"DOC-302\",\"machine_id\":\"M-05\",\"version\":\"v1.0\"}", null, "M-05", null, null));
        list.add(record("SOP", "DOC-303", "{\"sop_id\":\"DOC-303\",\"machine_id\":\"M-05\",\"version\":\"v3.0\"}", null, "M-05", null, null));
        list.add(record("SOP", "DOC-330", "{\"sop_id\":\"DOC-330\",\"machine_id\":\"M-05\",\"version\":\"v1.4\"}", null, "M-05", null, null));
        list.add(record("SOP", "DOC-304", "{\"sop_id\":\"DOC-304\",\"machine_id\":\"M-02\",\"version\":\"v1.0\"}", null, "M-02", null, null));
        list.add(record("SOP", "DOC-305", "{\"sop_id\":\"DOC-305\",\"machine_id\":\"M-03\",\"version\":\"v1.0\"}", null, "M-03", null, null));
        list.add(record("SOP", "DOC-306", "{\"sop_id\":\"DOC-306\",\"machine_id\":\"M-06\",\"version\":\"v1.0\"}", null, "M-06", null, null));
        list.add(record("SOP", "DOC-307", "{\"sop_id\":\"DOC-307\",\"machine_id\":\"M-07\",\"version\":\"v1.0\"}", null, "M-07", null, null));

        // 8. ERP_Supplier_Audits.csv (3 records: ERP-A01 for SUP-09, 2 others)
        list.add(record("ERP", "ERP-A01", "{\"audit_id\":\"ERP-A01\",\"supplier_id\":\"SUP-09\",\"rating\":\"B\"}", null, null, "SUP-09", null));
        list.add(record("ERP", "ERP-A02", "{\"audit_id\":\"ERP-A02\",\"supplier_id\":\"SUP-02\",\"rating\":\"A\"}", null, null, "SUP-02", null));
        list.add(record("ERP", "ERP-A03", "{\"audit_id\":\"ERP-A03\",\"supplier_id\":\"SUP-12\",\"rating\":\"A\"}", null, null, "SUP-12", null));

        return list;
    }

    private void configureMockRepositories(List<IngestedSourceRecord> records) {
        when(sourceRepository.findByOrganisationOrgIdAndBatchReference(eq(1L), anyString())).thenAnswer(inv -> {
            String b = inv.getArgument(1);
            return records.stream().filter(r -> b.equalsIgnoreCase(r.getBatchReference())).collect(Collectors.toList());
        });

        when(sourceRepository.findByOrganisationOrgIdAndMachineReference(eq(1L), anyString())).thenAnswer(inv -> {
            String m = inv.getArgument(1);
            return records.stream().filter(r -> m.equalsIgnoreCase(r.getMachineReference())).collect(Collectors.toList());
        });

        when(sourceRepository.findByOrganisationOrgIdAndSupplierReference(eq(1L), anyString())).thenAnswer(inv -> {
            String s = inv.getArgument(1);
            return records.stream().filter(r -> s.equalsIgnoreCase(r.getSupplierReference())).collect(Collectors.toList());
        });

        when(sourceRepository.findByOrganisationOrgIdAndProductReference(eq(1L), anyString())).thenAnswer(inv -> {
            String p = inv.getArgument(1);
            return records.stream().filter(r -> p.equalsIgnoreCase(r.getProductReference())).collect(Collectors.toList());
        });

        // Store canonical evidences in an in-memory map
        Map<String, CanonicalEvidence> canonStore = new HashMap<>();
        when(canonicalRepository.findByExternalIdAndOrganisationOrgId(anyString(), eq(1L))).thenAnswer(inv -> {
            String extId = inv.getArgument(0);
            return Optional.ofNullable(canonStore.get(extId));
        });

        when(canonicalRepository.save(any(CanonicalEvidence.class))).thenAnswer(inv -> {
            CanonicalEvidence c = inv.getArgument(0);
            if (c.getId() == null) c.setId((long) (canonStore.size() + 1));
            canonStore.put(c.getExternalId(), c);
            return c;
        });

        when(canonicalRepository.findAllByOrganisationOrgId(1L)).thenAnswer(inv -> new ArrayList<>(canonStore.values()));
    }

    @Test
    void test1_complaintDiscovery_discoversExactly17RecordsForBatch1010() {
        List<IngestedSourceRecord> all43 = build43SourceRecords();
        assertEquals(43, all43.size(), "Pool must contain exactly 43 source records across 8 files");
        configureMockRepositories(all43);

        EvidenceDiscoveryService.DiscoveryResult result = discoveryService.discoverForIncident(1L, 1L);

        // Discovered count must be exactly 17
        assertEquals(17, result.discovered, "Exactly 17 records should be discovered for BATCH-1010");
        assertEquals(17, result.created, "First run should create 17 canonical records");

        // Verify breakdown across sources
        assertEquals(1, result.sources.get("MES"), "1 MES record (BATCH-1010)");
        assertEquals(2, result.sources.get("LIMS"), "2 LIMS QA records (QA-2001, QA-2002)");
        assertEquals(1, result.sources.get("PACKAGING"), "1 PACKAGING record (PKG-401)");
        assertEquals(1, result.sources.get("SHIPMENT"), "1 SHIPMENT record (SHIP-2017)");
        assertEquals(4, result.sources.get("WAREHOUSE"), "4 WAREHOUSE records (LOG-3023..LOG-3026)");
        assertEquals(3, result.sources.get("CMMS"), "3 CMMS records for M-05 (MAINT-1014, MAINT-1020, MAINT-1021)");
        assertEquals(4, result.sources.get("SOP"), "4 SOP records for M-05 (DOC-301..303, 330)");
        assertEquals(1, result.sources.get("ERP"), "1 ERP supplier record for SUP-09 (ERP-A01)");

        // Sum of sources must be exactly 17
        int totalSources = result.sources.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(17, totalSources);
    }

    @Test
    void test2_complaintDiscovery_isDeterministicAndIdempotent() {
        List<IngestedSourceRecord> all43 = build43SourceRecords();
        configureMockRepositories(all43);

        // Run 1: initial discovery
        EvidenceDiscoveryService.DiscoveryResult result1 = discoveryService.discoverForIncident(1L, 1L);
        assertEquals(17, result1.discovered);
        assertEquals(17, result1.created);
        assertEquals(0, result1.reused);

        // Run 2: idempotent re-run
        EvidenceDiscoveryService.DiscoveryResult result2 = discoveryService.discoverForIncident(1L, 1L);
        assertEquals(17, result2.discovered);
        assertEquals(0, result2.created, "Second run must not create duplicate canonical records");
        assertEquals(17, result2.reused, "Second run must reuse existing 17 canonical records");

        // Verify CanonicalEvidence repository size is still exactly 17
        assertEquals(17, canonicalRepository.findAllByOrganisationOrgId(1L).size());
    }

    @Test
    void test3_complaintDiscovery_strictCrossBatchExclusion() {
        List<IngestedSourceRecord> all43 = build43SourceRecords();
        configureMockRepositories(all43);

        EvidenceDiscoveryService.DiscoveryResult result = discoveryService.discoverForIncident(1L, 1L);
        assertEquals(17, result.discovered);

        List<CanonicalEvidence> discovered = canonicalRepository.findAllByOrganisationOrgId(1L);

        // None of the other batches should leak into primary evidence
        Set<String> excludedBatches = Set.of("BATCH-1011", "BATCH-1012", "BATCH-1015", "BATCH-1019");
        for (CanonicalEvidence ev : discovered) {
            for (String ex : excludedBatches) {
                assertFalse(ev.getExternalId().contains(ex),
                        "Discovered evidence " + ev.getExternalId() + " must NOT belong to excluded batch " + ex);
            }
        }

        // None of the unrelated machines or suppliers should leak
        for (CanonicalEvidence ev : discovered) {
            assertFalse(ev.getExternalId().contains("M-02"), "Machine M-02 must NOT be included");
            assertFalse(ev.getExternalId().contains("M-03"), "Machine M-03 must NOT be included");
            assertFalse(ev.getExternalId().contains("M-07"), "Machine M-07 must NOT be included");
            assertFalse(ev.getExternalId().contains("ERP-A02"), "Supplier SUP-02 (ERP-A02) must NOT be included");
            assertFalse(ev.getExternalId().contains("ERP-A03"), "Supplier SUP-12 (ERP-A03) must NOT be included");
        }
    }

    @Test
    void test4_complaintDiscovery_multiHopGraphTraversal() {
        List<IngestedSourceRecord> all43 = build43SourceRecords();
        configureMockRepositories(all43);

        // Run discovery to populate canonical evidence
        discoveryService.discoverForIncident(1L, 1L);

        // Now run graph projection
        GraphProjectionResult projResult = graphProjectionService.projectForOrganisation(1L);
        assertEquals(17, projResult.getEvidenceProjected());
        assertEquals(1, projResult.getIncidentsProjected());

        // Verify entity nodes were merged
        verify(neo4jTx, atLeastOnce()).run(contains("MERGE (b:Batch"), anyMap());
        verify(neo4jTx, atLeastOnce()).run(contains("MERGE (m:Machine"), anyMap());
        verify(neo4jTx, atLeastOnce()).run(contains("MERGE (s:Supplier"), anyMap());
        verify(neo4jTx, atLeastOnce()).run(contains("MERGE (p:Product"), anyMap());
        verify(neo4jTx, atLeastOnce()).run(contains("MERGE (c:Customer"), anyMap());
        verify(neo4jTx, atLeastOnce()).run(contains("MERGE (w:Warehouse"), anyMap());

        // Verify relationships
        verify(neo4jTx, atLeastOnce()).run(contains("[:TARGETS]"), anyMap());
        verify(neo4jTx, atLeastOnce()).run(contains("[:REFERENCES]"), anyMap());
        verify(neo4jTx, atLeastOnce()).run(contains("[:ASSOCIATED_WITH]"), anyMap());
        verify(neo4jTx, atLeastOnce()).run(contains("[:HAS_EVIDENCE]"), anyMap());

        // Verify tenant isolation: all Neo4j queries must contain orgId: 1
        ArgumentCaptor<Map> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(neo4jTx, atLeastOnce()).run(anyString(), paramsCaptor.capture());
        for (Map params : paramsCaptor.getAllValues()) {
            if (params.containsKey("orgId")) {
                assertEquals(1L, params.get("orgId"), "orgId in Neo4j parameters must match 1L");
            }
        }
    }

    @Test
    void test5_complaintDiscovery_tenantIsolation() {
        // Records for org 2 should never be returned for org 1
        when(sourceRepository.findByOrganisationOrgIdAndBatchReference(eq(1L), anyString())).thenReturn(List.of());
        when(sourceRepository.findByOrganisationOrgIdAndBatchReference(eq(2L), anyString())).thenReturn(build43SourceRecords());

        EvidenceDiscoveryService.DiscoveryResult result = discoveryService.discoverForIncident(1L, 1L);
        assertEquals(0, result.discovered, "Org 1 discovery must find 0 records when data belongs to Org 2");
    }

    @Test
    void test6_complaintWithoutBatch_completesGracefully() {
        when(complaintRepository.existsByComplaintKeyAndOrganisationOrgId("CMP-NO-BATCH", 1L)).thenReturn(false);
        when(complaintRepository.save(any())).thenAnswer(i -> {
            Complaint c = i.getArgument(0);
            c.setId(999L);
            return c;
        });

        CreateComplaintRequest req = CreateComplaintRequest.builder()
                .complaintKey("CMP-NO-BATCH")
                .title("Complaint with no batch")
                .description("No batch specified")
                .batchReference(null)
                .externalReference("EXT-999")
                .build();

        ComplaintResponse response = complaintService.createManual(req);
        assertNotNull(response);
        assertEquals("CMP-NO-BATCH", response.getComplaintKey());
        assertNull(response.getBatchReference());

        // Discovery must NOT have been called
        verify(sourceRepository, never()).findByOrganisationOrgIdAndBatchReference(anyLong(), anyString());
    }

    @Test
    void test7_complaintCreation_triggersDiscoverySynchronously() throws Exception {
        List<IngestedSourceRecord> all43 = build43SourceRecords();
        configureMockRepositories(all43);

        when(complaintRepository.existsByComplaintKeyAndOrganisationOrgId("CMP-502", 1L)).thenReturn(false);
        when(complaintRepository.save(any())).thenAnswer(i -> {
            Complaint c = i.getArgument(0);
            c.setId(502L);
            return c;
        });
        when(investigationRepository.findByInvestigationKeyAndOrganisationOrgId(anyString(), eq(1L)))
                .thenReturn(Optional.of(investigation1010));

        CreateComplaintRequest req = CreateComplaintRequest.builder()
                .complaintKey("CMP-502")
                .title("BATCH-1010 Defect Report")
                .description("Investigating BATCH-1010 packaging & maintenance anomalies")
                .batchReference("BATCH-1010")
                .externalReference("EXT-502")
                .build();

        ComplaintResponse resp = complaintService.createManual(req);

        assertNotNull(resp);
        assertEquals("CMP-502", resp.getComplaintKey());
        assertEquals("BATCH-1010", resp.getBatchReference());

        Thread.sleep(1000);

        // Verify discovery was executed and discovered the 17 records
        List<CanonicalEvidence> discovered = canonicalRepository.findAllByOrganisationOrgId(1L);
        assertEquals(17, discovered.size(), "Complaint creation with BATCH-1010 must materialize 17 canonical evidence records");
    }

    @Test
    void test8_complaintWithBatchNoMatchingEvidence_createsComplaintWithoutEvidence() {
        when(sourceRepository.findByOrganisationOrgIdAndBatchReference(eq(1L), eq("BATCH-NONE")))
                .thenReturn(List.of());
        when(sourceRepository.findByOrganisationOrgIdAndMachineReference(eq(1L), anyString()))
                .thenReturn(List.of());
        when(sourceRepository.findByOrganisationOrgIdAndSupplierReference(eq(1L), anyString()))
                .thenReturn(List.of());
        when(sourceRepository.findByOrganisationOrgIdAndProductReference(eq(1L), anyString()))
                .thenReturn(List.of());

        Map<String, CanonicalEvidence> canonStore = new HashMap<>();
        when(canonicalRepository.findByExternalIdAndOrganisationOrgId(anyString(), eq(1L)))
                .thenAnswer(inv -> Optional.ofNullable(canonStore.get(inv.getArgument(0))));
        when(canonicalRepository.save(any(CanonicalEvidence.class))).thenAnswer(inv -> {
            CanonicalEvidence c = inv.getArgument(0);
            if (c.getId() == null) c.setId((long) (canonStore.size() + 1));
            canonStore.put(c.getExternalId(), c);
            return c;
        });
        when(canonicalRepository.findAllByOrganisationOrgId(1L)).thenAnswer(inv -> new ArrayList<>(canonStore.values()));

        Investigation inv = Investigation.builder()
                .id(2L).organisation(org1).investigationKey("INV-CMP-NO-MATCH")
                .title("No match").batchReference("BATCH-NONE").status("DRAFT").build();
        when(investigationRepository.findByInvestigationKeyAndOrganisationOrgId("INV-CMP-NO-MATCH", 1L))
                .thenReturn(Optional.of(inv));

        when(complaintRepository.existsByComplaintKeyAndOrganisationOrgId("CMP-NO-MATCH", 1L)).thenReturn(false);
        when(complaintRepository.save(any())).thenAnswer(i -> {
            Complaint c = i.getArgument(0); c.setId(888L); return c;
        });

        CreateComplaintRequest req = CreateComplaintRequest.builder()
                .complaintKey("CMP-NO-MATCH")
                .title("Batch with no matching source records")
                .description("Should still be created")
                .batchReference("BATCH-NONE")
                .build();

        ComplaintResponse resp = complaintService.createManual(req);
        assertNotNull(resp);
        assertEquals("CMP-NO-MATCH", resp.getComplaintKey());
        assertEquals(0, canonicalRepository.findAllByOrganisationOrgId(1L).size(),
                "No fake evidence should be created when batch has no matching source records");
    }

    @Test
    void test9_complaintFlow_idempotency_noDuplicateEvidence() throws Exception {
        List<IngestedSourceRecord> all43 = build43SourceRecords();
        configureMockRepositories(all43);

        when(complaintRepository.existsByComplaintKeyAndOrganisationOrgId("CMP-IDEM", 1L)).thenReturn(false);
        when(complaintRepository.save(any())).thenAnswer(i -> {
            Complaint c = i.getArgument(0); c.setId(700L); return c;
        });
        when(investigationRepository.findByInvestigationKeyAndOrganisationOrgId("INV-CMP-IDEM", 1L))
                .thenReturn(Optional.of(investigation1010));

        CreateComplaintRequest req = CreateComplaintRequest.builder()
                .complaintKey("CMP-IDEM")
                .title("Idempotency test")
                .description("Discovery run twice should not duplicate")
                .batchReference("BATCH-1010")
                .build();

        ComplaintResponse resp1 = complaintService.createManual(req);
        assertNotNull(resp1);
        Thread.sleep(1000);
        assertEquals(17, canonicalRepository.findAllByOrganisationOrgId(1L).size(),
                "First complaint creation must produce 17 canonical evidence");

        discoveryService.discoverForIncident(1L, 1L);
        assertEquals(17, canonicalRepository.findAllByOrganisationOrgId(1L).size(),
                "Re-running discovery must not duplicate canonical evidence");
    }

    @Test
    void test10_existingIncident1Graph_notDegradedByNewDiscovery() {
        List<IngestedSourceRecord> all43 = build43SourceRecords();
        configureMockRepositories(all43);

        EvidenceDiscoveryService.DiscoveryResult result1 = discoveryService.discoverForIncident(1L, 1L);
        assertEquals(17, result1.discovered);

        GraphProjectionResult proj1 = graphProjectionService.projectForOrganisation(1L);
        assertEquals(17, proj1.getEvidenceProjected());

        EvidenceDiscoveryService.DiscoveryResult result2 = discoveryService.discoverForIncident(1L, 1L);
        assertEquals(17, result2.discovered);
        assertEquals(0, result2.created, "Second discovery run must not create duplicates");
        assertEquals(17, result2.reused, "Second discovery run must reuse all 17 existing records");

        GraphProjectionResult proj2 = graphProjectionService.projectForOrganisation(1L);
        assertEquals(17, proj2.getEvidenceProjected(),
                "Re-projecting must not degrade existing graph evidence count");
    }

    @Test
    void test11_complaintCreation_succeedsEvenWhenGraphNotReady() {
        when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(false);

        when(complaintRepository.existsByComplaintKeyAndOrganisationOrgId("CMP-NEW-BATCH", 1L)).thenReturn(false);
        when(complaintRepository.save(any())).thenAnswer(i -> {
            Complaint c = i.getArgument(0); c.setId(600L); return c;
        });
        Investigation newInv = Investigation.builder()
                .id(3L).organisation(org1).investigationKey("INV-CMP-NEW-BATCH")
                .title("New batch complaint").batchReference("BATCH-NEW").status("DRAFT").build();
        when(investigationRepository.findByInvestigationKeyAndOrganisationOrgId("INV-CMP-NEW-BATCH", 1L))
                .thenReturn(Optional.of(newInv));

        when(sourceRepository.findByOrganisationOrgIdAndBatchReference(eq(1L), eq("BATCH-NEW")))
                .thenReturn(List.of());
        when(sourceRepository.findByOrganisationOrgIdAndMachineReference(eq(1L), anyString()))
                .thenReturn(List.of());
        when(sourceRepository.findByOrganisationOrgIdAndSupplierReference(eq(1L), anyString()))
                .thenReturn(List.of());
        when(sourceRepository.findByOrganisationOrgIdAndProductReference(eq(1L), anyString()))
                .thenReturn(List.of());
        Map<String, CanonicalEvidence> canonStore = new HashMap<>();
        when(canonicalRepository.findByExternalIdAndOrganisationOrgId(anyString(), eq(1L)))
                .thenAnswer(inv -> Optional.ofNullable(canonStore.get(inv.getArgument(0))));
        when(canonicalRepository.save(any(CanonicalEvidence.class))).thenAnswer(inv -> {
            CanonicalEvidence c = inv.getArgument(0);
            if (c.getId() == null) c.setId((long) (canonStore.size() + 1));
            canonStore.put(c.getExternalId(), c);
            return c;
        });
        when(canonicalRepository.findAllByOrganisationOrgId(1L)).thenAnswer(inv -> new ArrayList<>(canonStore.values()));

        CreateComplaintRequest req = CreateComplaintRequest.builder()
                .complaintKey("CMP-NEW-BATCH")
                .title("New complaint with no existing graph")
                .description("Must succeed even when graph is not ready")
                .batchReference("BATCH-NEW")
                .build();

        ComplaintResponse resp = complaintService.createManual(req);
        assertNotNull(resp);
        assertEquals("CMP-NEW-BATCH", resp.getComplaintKey());
        assertEquals("BATCH-NEW", resp.getBatchReference());
    }
}
