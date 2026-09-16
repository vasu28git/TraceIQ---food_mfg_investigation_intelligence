package com.taceiq;

import com.taceiq.dto.BatchConnectionMapResponse;
import com.taceiq.dto.InvestigationPathDto;
import com.taceiq.dto.InvestigationSignalDto;
import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.EvidenceCorrelationProvenance;
import com.taceiq.entity.IngestedSourceRecord;
import com.taceiq.entity.Investigation;
import com.taceiq.entity.Organisation;
import com.taceiq.ingestion.SourceFieldMapper;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.EvidenceCorrelationProvenanceRepository;
import com.taceiq.repository.IngestedSourceRecordRepository;
import com.taceiq.repository.InvestigationEvidenceRepository;
import com.taceiq.repository.InvestigationRepository;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.InvestigationEvidenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GenericInvestigationDatasetsTest {

    private InvestigationEvidenceService evidenceService;
    private CanonicalEvidenceRepository canonicalRepository;
    private InvestigationEvidenceRepository linkRepository;
    private EvidenceCorrelationProvenanceRepository provenanceRepository;
    private IngestedSourceRecordRepository sourceRecordRepository;
    private AuthorizationService authorizationService;
    private Organisation org;

    @BeforeEach
    void setUp() {
        InvestigationRepository investigationRepository = mock(InvestigationRepository.class);
        canonicalRepository = mock(CanonicalEvidenceRepository.class);
        linkRepository = mock(InvestigationEvidenceRepository.class);
        provenanceRepository = mock(EvidenceCorrelationProvenanceRepository.class);
        sourceRecordRepository = mock(IngestedSourceRecordRepository.class);
        authorizationService = mock(AuthorizationService.class);

        org = Organisation.builder().orgId(10L).name("GenericTestOrg").build();

        when(authorizationService.getCurrentOrgId()).thenReturn(10L);
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(anyLong(), anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(provenanceRepository.findByOrganisationOrgIdAndInvestigationId(anyLong(), anyLong()))
                .thenReturn(List.of());

        evidenceService = new InvestigationEvidenceService(
                investigationRepository,
                canonicalRepository,
                linkRepository,
                authorizationService,
                mock(com.taceiq.graph.service.GraphReadinessService.class),
                provenanceRepository,
                sourceRecordRepository
        );
    }

    @Test
    @DisplayName("DATASET A: 1 batch, 1 product, 1 machine, direct QA, direct shipment, maintenance")
    void datasetA_singleEntityChain() {
        Investigation invA = Investigation.builder()
                .id(1001L).organisation(org).investigationKey("INV-DS-A").batchReference("BATCH-A1").productReference("PROD-A1").build();

        InvestigationRepository invRepo = mock(InvestigationRepository.class);
        when(invRepo.findByIdAndOrganisationOrgId(1001L, 10L)).thenReturn(Optional.of(invA));

        CanonicalEvidence mesEv = CanonicalEvidence.builder()
                .id(1L).organisation(org).incident(invA).externalId("SRC_MES_A1").sourceType("MES").status("COMPLETED")
                .title("MES Batch Record")
                .normalizedPayload("{\"batch_id\":\"BATCH-A1\",\"product_id\":\"PROD-A1\",\"machine_id\":\"M-A1\"}").lastSeenAt(Instant.now()).build();

        CanonicalEvidence qaEv = CanonicalEvidence.builder()
                .id(2L).organisation(org).incident(invA).externalId("SRC_LIMS_A1").sourceType("LIMS").status("FAILED")
                .title("Viscosity Test Failure")
                .normalizedPayload("{\"batch_id\":\"BATCH-A1\",\"result\":\"FAILED\"}").lastSeenAt(Instant.now()).build();

        CanonicalEvidence shipEv = CanonicalEvidence.builder()
                .id(3L).organisation(org).incident(invA).externalId("SRC_SHIP_A1").sourceType("SHIPMENT").status("RECALLED")
                .title("Transit Recall")
                .normalizedPayload("{\"batch_id\":\"BATCH-A1\",\"shipment_id\":\"SHIP-A1\",\"shipmentStatus\":\"RECALLED\"}").lastSeenAt(Instant.now()).build();

        CanonicalEvidence cmmsEv = CanonicalEvidence.builder()
                .id(4L).organisation(org).incident(invA).externalId("SRC_CMMS_A1").sourceType("CMMS").status("OVERDUE")
                .title("M-A1 Calibration Overdue")
                .normalizedPayload("{\"machine_id\":\"M-A1\",\"maintenanceStatus\":\"OVERDUE\"}").lastSeenAt(Instant.now()).build();

        List<CanonicalEvidence> list = List.of(mesEv, qaEv, shipEv, cmmsEv);
        when(canonicalRepository.findByIncidentIdAndOrganisationOrgId(1001L, 10L)).thenReturn(list);

        InvestigationEvidenceService service = new InvestigationEvidenceService(
                invRepo, canonicalRepository, linkRepository, authorizationService,
                mock(com.taceiq.graph.service.GraphReadinessService.class), provenanceRepository, sourceRecordRepository
        );

        BatchConnectionMapResponse resp = service.getBatchConnectionMap(1001L);

        assertEquals("BATCH-A1", resp.getBatchReference());
        assertEquals(1, resp.getCounts().get("products"));
        assertEquals(1, resp.getCounts().get("machines"));
        assertEquals(1, resp.getCounts().get("shipments"));
        assertEquals(1, resp.getCounts().get("qaLimsRecords"));
        assertEquals(4, resp.getCounts().get("evidenceRecords"));
        assertFalse(resp.getSignals().isEmpty());
    }

    @Test
    @DisplayName("DATASET B: 1 batch, 2 products, 5 machines, multiple suppliers/shipments/QA/warehouses")
    void datasetB_complexMultiEntity() {
        Investigation invB = Investigation.builder()
                .id(1002L).organisation(org).investigationKey("INV-DS-B").batchReference("BATCH-B2").productReference("PROD-B1").build();

        InvestigationRepository invRepo = mock(InvestigationRepository.class);
        when(invRepo.findByIdAndOrganisationOrgId(1002L, 10L)).thenReturn(Optional.of(invB));

        CanonicalEvidence mesEv = CanonicalEvidence.builder()
                .id(10L).organisation(org).incident(invB).externalId("SRC_MES_B2").sourceType("MES").status("COMPLETED")
                .normalizedPayload("{\"batch_id\":\"BATCH-B2\",\"product_id\":\"PROD-B1\",\"machine_id\":\"M-10\",\"supplier_id\":\"SUP-1\"}").lastSeenAt(Instant.now()).build();

        CanonicalEvidence cmms11 = CanonicalEvidence.builder().id(11L).organisation(org).incident(invB).externalId("SRC_CMMS_11").sourceType("CMMS").status("READY")
                .normalizedPayload("{\"batch_id\":\"BATCH-B2\",\"machine_id\":\"M-11\"}").lastSeenAt(Instant.now()).build();
        CanonicalEvidence cmms12 = CanonicalEvidence.builder().id(12L).organisation(org).incident(invB).externalId("SRC_CMMS_12").sourceType("CMMS").status("READY")
                .normalizedPayload("{\"batch_id\":\"BATCH-B2\",\"machine_id\":\"M-12\"}").lastSeenAt(Instant.now()).build();
        CanonicalEvidence cmms13 = CanonicalEvidence.builder().id(13L).organisation(org).incident(invB).externalId("SRC_CMMS_13").sourceType("CMMS").status("READY")
                .normalizedPayload("{\"batch_id\":\"BATCH-B2\",\"machine_id\":\"M-13\"}").lastSeenAt(Instant.now()).build();
        CanonicalEvidence cmms14 = CanonicalEvidence.builder().id(14L).organisation(org).incident(invB).externalId("SRC_CMMS_14").sourceType("CMMS").status("READY")
                .normalizedPayload("{\"batch_id\":\"BATCH-B2\",\"machine_id\":\"M-14\"}").lastSeenAt(Instant.now()).build();

        CanonicalEvidence sup2 = CanonicalEvidence.builder().id(15L).organisation(org).incident(invB).externalId("SRC_ERP_SUP2").sourceType("ERP").status("READY")
                .normalizedPayload("{\"batch_id\":\"BATCH-B2\",\"supplier_id\":\"SUP-2\"}").lastSeenAt(Instant.now()).build();

        CanonicalEvidence ship1 = CanonicalEvidence.builder().id(16L).organisation(org).incident(invB).externalId("SRC_SHIP_1").sourceType("SHIPMENT").status("READY")
                .normalizedPayload("{\"batch_id\":\"BATCH-B2\",\"shipment_id\":\"SHIP-1\"}").lastSeenAt(Instant.now()).build();
        CanonicalEvidence ship2 = CanonicalEvidence.builder().id(17L).organisation(org).incident(invB).externalId("SRC_SHIP_2").sourceType("SHIPMENT").status("READY")
                .normalizedPayload("{\"batch_id\":\"BATCH-B2\",\"shipment_id\":\"SHIP-2\"}").lastSeenAt(Instant.now()).build();

        CanonicalEvidence wh1 = CanonicalEvidence.builder().id(18L).organisation(org).incident(invB).externalId("SRC_WH_1").sourceType("WAREHOUSE").status("READY")
                .normalizedPayload("{\"batch_id\":\"BATCH-B2\",\"warehouse_zone\":\"WZ-1\"}").lastSeenAt(Instant.now()).build();
        CanonicalEvidence wh2 = CanonicalEvidence.builder().id(19L).organisation(org).incident(invB).externalId("SRC_WH_2").sourceType("WAREHOUSE").status("READY")
                .normalizedPayload("{\"batch_id\":\"BATCH-B2\",\"warehouse_zone\":\"WZ-2\"}").lastSeenAt(Instant.now()).build();

        CanonicalEvidence qa1 = CanonicalEvidence.builder().id(20L).organisation(org).incident(invB).externalId("SRC_QA_1").sourceType("LIMS").status("READY")
                .normalizedPayload("{\"batch_id\":\"BATCH-B2\",\"sample_id\":\"QA-1\"}").lastSeenAt(Instant.now()).build();
        CanonicalEvidence qa2 = CanonicalEvidence.builder().id(21L).organisation(org).incident(invB).externalId("SRC_QA_2").sourceType("LIMS").status("READY")
                .normalizedPayload("{\"batch_id\":\"BATCH-B2\",\"sample_id\":\"QA-2\"}").lastSeenAt(Instant.now()).build();
        CanonicalEvidence qa3 = CanonicalEvidence.builder().id(22L).organisation(org).incident(invB).externalId("SRC_QA_3").sourceType("LIMS").status("READY")
                .normalizedPayload("{\"batch_id\":\"BATCH-B2\",\"sample_id\":\"QA-3\"}").lastSeenAt(Instant.now()).build();

        List<CanonicalEvidence> list = List.of(mesEv, cmms11, cmms12, cmms13, cmms14, sup2, ship1, ship2, wh1, wh2, qa1, qa2, qa3);
        when(canonicalRepository.findByIncidentIdAndOrganisationOrgId(1002L, 10L)).thenReturn(list);

        InvestigationEvidenceService service = new InvestigationEvidenceService(
                invRepo, canonicalRepository, linkRepository, authorizationService,
                mock(com.taceiq.graph.service.GraphReadinessService.class), provenanceRepository, sourceRecordRepository
        );

        BatchConnectionMapResponse resp = service.getBatchConnectionMap(1002L);

        assertEquals(5, resp.getCounts().get("machines"));
        assertEquals(2, resp.getCounts().get("suppliers"));
        assertEquals(2, resp.getCounts().get("shipments"));
        assertEquals(2, resp.getCounts().get("warehouseRecords"));
        assertEquals(3, resp.getCounts().get("qaLimsRecords"));
        assertEquals(13, resp.getCounts().get("evidenceRecords"));
    }

    @Test
    @DisplayName("DATASET C: two batches sharing a product with different machines -> cross-batch isolation")
    void datasetC_crossBatchIsolation() {
        Investigation invC1 = Investigation.builder().id(1003L).organisation(org).investigationKey("INV-C1").batchReference("BATCH-C1").productReference("PROD-SHARED").build();

        InvestigationRepository invRepo = mock(InvestigationRepository.class);
        when(invRepo.findByIdAndOrganisationOrgId(1003L, 10L)).thenReturn(Optional.of(invC1));

        CanonicalEvidence mesC1 = CanonicalEvidence.builder().id(30L).organisation(org).incident(invC1).externalId("SRC_MES_C1").sourceType("MES").status("COMPLETED")
                .normalizedPayload("{\"batch_id\":\"BATCH-C1\",\"product_id\":\"PROD-SHARED\",\"machine_id\":\"M-C1\"}").lastSeenAt(Instant.now()).build();

        CanonicalEvidence cmmsC2 = CanonicalEvidence.builder().id(31L).organisation(org).incident(invC1).externalId("SRC_CMMS_C2").sourceType("CMMS").status("OVERDUE")
                .title("M-C2 Machine Failure")
                .normalizedPayload("{\"batch_id\":\"BATCH-C2\",\"machine_id\":\"M-C2\",\"maintenanceStatus\":\"OVERDUE\"}").lastSeenAt(Instant.now()).build();

        when(canonicalRepository.findByIncidentIdAndOrganisationOrgId(1003L, 10L)).thenReturn(List.of(mesC1, cmmsC2));

        IngestedSourceRecord rC1 = IngestedSourceRecord.builder().id(1L).organisation(org).sourceType("MES").sourceRecordId("B-C1").batchReference("BATCH-C1").productReference("PROD-SHARED").machineReference("M-C1").build();
        IngestedSourceRecord rC2 = IngestedSourceRecord.builder().id(2L).organisation(org).sourceType("MES").sourceRecordId("B-C2").batchReference("BATCH-C2").productReference("PROD-SHARED").machineReference("M-C2").build();
        when(sourceRecordRepository.findByOrganisationOrgIdAndProductReference(10L, "PROD-SHARED")).thenReturn(List.of(rC1, rC2));
        when(sourceRecordRepository.countByOrganisationOrgIdAndBatchReference(10L, "BATCH-C2")).thenReturn(3L);

        InvestigationEvidenceService service = new InvestigationEvidenceService(
                invRepo, canonicalRepository, linkRepository, authorizationService,
                mock(com.taceiq.graph.service.GraphReadinessService.class), provenanceRepository, sourceRecordRepository
        );

        BatchConnectionMapResponse resp = service.getBatchConnectionMap(1003L);

        // M-C1 is primary, M-C2 is NOT primary
        assertTrue(resp.getNodes().stream().anyMatch(n -> "Machine".equals(n.getType()) && "M-C1".equals(n.getStableId())));
        assertFalse(resp.getNodes().stream().anyMatch(n -> "Machine".equals(n.getType()) && "M-C2".equals(n.getStableId())));

        // Cross batch contains M-C2 and BATCH-C2
        assertNotNull(resp.getCrossBatchNodes());
        assertTrue(resp.getCrossBatchNodes().stream().anyMatch(n -> "M-C2".equals(n.getStableId())));
        assertTrue(resp.getRelatedBatches().stream().anyMatch(rb -> "BATCH-C2".equals(rb.getBatchReference())));

        // Signal from BATCH-C2 must NOT be in primary signals
        assertTrue(resp.getSignals().stream().noneMatch(s -> "SRC_CMMS_C2".equals(s.getSourceEvidenceId())));
    }

    @Test
    @DisplayName("DATASET D: same evidence reachable by direct and indirect routes -> path deduplication prefers direct")
    void datasetD_pathDeduplicationDirectOverIndirect() {
        Investigation invD = Investigation.builder().id(1004L).organisation(org).investigationKey("INV-D").batchReference("BATCH-D").productReference("PROD-D").build();

        InvestigationRepository invRepo = mock(InvestigationRepository.class);
        when(invRepo.findByIdAndOrganisationOrgId(1004L, 10L)).thenReturn(Optional.of(invD));

        CanonicalEvidence mesD = CanonicalEvidence.builder().id(40L).organisation(org).incident(invD).externalId("SRC_MES_D").sourceType("MES").status("COMPLETED")
                .normalizedPayload("{\"batch_id\":\"BATCH-D\",\"product_id\":\"PROD-D\",\"machine_id\":\"M-D\"}").lastSeenAt(Instant.now()).build();

        CanonicalEvidence cmmsD = CanonicalEvidence.builder().id(41L).organisation(org).incident(invD).externalId("SRC_CMMS_D").sourceType("CMMS").status("OVERDUE")
                .normalizedPayload("{\"batch_id\":\"BATCH-D\",\"machine_id\":\"M-D\",\"maintenanceStatus\":\"OVERDUE\"}").lastSeenAt(Instant.now()).build();

        when(canonicalRepository.findByIncidentIdAndOrganisationOrgId(1004L, 10L)).thenReturn(List.of(mesD, cmmsD));

        // Provenance contains both direct path and indirect path via Product
        EvidenceCorrelationProvenance provDirect = EvidenceCorrelationProvenance.builder()
                .id(100L).organisation(org).investigation(invD).canonicalEvidence(cmmsD).reason("MACHINE_MATCH_FROM_BATCH")
                .matchedField("machine_reference").matchedValue("M-D").sourceRecordId("MAINT-D").intermediateEntityType("machine").intermediateEntityValue("M-D")
                .connectionPath("[\"Batch:BATCH-D\",\"Machine:M-D\",\"CMMS Evidence:SRC_CMMS_D\"]").discoveredAt(Instant.now()).build();

        EvidenceCorrelationProvenance provIndirect = EvidenceCorrelationProvenance.builder()
                .id(101L).organisation(org).investigation(invD).canonicalEvidence(cmmsD).reason("PRODUCT_MACHINE_MATCH")
                .matchedField("machine_reference").matchedValue("M-D").sourceRecordId("MAINT-D").intermediateEntityType("machine").intermediateEntityValue("M-D")
                .connectionPath("[\"Batch:BATCH-D\",\"Product:PROD-D\",\"Machine:M-D\",\"CMMS Evidence:SRC_CMMS_D\"]").discoveredAt(Instant.now()).build();

        when(provenanceRepository.findByOrganisationOrgIdAndInvestigationId(10L, 1004L)).thenReturn(List.of(provDirect, provIndirect));

        InvestigationEvidenceService service = new InvestigationEvidenceService(
                invRepo, canonicalRepository, linkRepository, authorizationService,
                mock(com.taceiq.graph.service.GraphReadinessService.class), provenanceRepository, sourceRecordRepository
        );

        BatchConnectionMapResponse resp = service.getBatchConnectionMap(1004L);

        List<InvestigationPathDto> paths = resp.getPaths();
        assertNotNull(paths);
        // Direct path retained, redundant longer indirect path suppressed
        assertTrue(paths.stream().anyMatch(p -> p.getPath().equals(List.of("Batch:BATCH-D", "Machine:M-D", "CMMS:SRC_CMMS_D"))));
        assertFalse(paths.stream().anyMatch(p -> p.getPath().equals(List.of("Batch:BATCH-D", "Product:PROD-D", "Machine:M-D", "CMMS:SRC_CMMS_D"))));
    }

    @Test
    @DisplayName("DATASET E: same evidence reachable through two genuinely different paths -> both valid paths remain")
    void datasetE_distinctPathsRetained() {
        Investigation invE = Investigation.builder().id(1005L).organisation(org).investigationKey("INV-E").batchReference("BATCH-E").productReference("PROD-E").build();

        InvestigationRepository invRepo = mock(InvestigationRepository.class);
        when(invRepo.findByIdAndOrganisationOrgId(1005L, 10L)).thenReturn(Optional.of(invE));

        CanonicalEvidence multiPathEv = CanonicalEvidence.builder().id(50L).organisation(org).incident(invE).externalId("SRC_ERP_SUP_E").sourceType("ERP").status("READY")
                .title("Supplier Audit Record")
                .normalizedPayload("{\"batch_id\":\"BATCH-E\",\"supplier_id\":\"SUP-E\",\"machine_id\":\"M-E\"}").lastSeenAt(Instant.now()).build();

        when(canonicalRepository.findByIncidentIdAndOrganisationOrgId(1005L, 10L)).thenReturn(List.of(multiPathEv));

        EvidenceCorrelationProvenance prov1 = EvidenceCorrelationProvenance.builder()
                .id(200L).organisation(org).investigation(invE).canonicalEvidence(multiPathEv).reason("MACHINE_MATCH_FROM_BATCH")
                .matchedField("machine_reference").matchedValue("M-E").sourceRecordId("ERP-SUP-E").intermediateEntityType("machine").intermediateEntityValue("M-E")
                .connectionPath("[\"Batch:BATCH-E\",\"Machine:M-E\",\"ERP Evidence:SRC_ERP_SUP_E\"]").discoveredAt(Instant.now()).build();

        EvidenceCorrelationProvenance prov2 = EvidenceCorrelationProvenance.builder()
                .id(201L).organisation(org).investigation(invE).canonicalEvidence(multiPathEv).reason("SUPPLIER_MATCH_FROM_BATCH")
                .matchedField("supplier_reference").matchedValue("SUP-E").sourceRecordId("ERP-SUP-E").intermediateEntityType("supplier").intermediateEntityValue("SUP-E")
                .connectionPath("[\"Batch:BATCH-E\",\"Supplier:SUP-E\",\"ERP Evidence:SRC_ERP_SUP_E\"]").discoveredAt(Instant.now()).build();

        when(provenanceRepository.findByOrganisationOrgIdAndInvestigationId(10L, 1005L)).thenReturn(List.of(prov1, prov2));

        InvestigationEvidenceService service = new InvestigationEvidenceService(
                invRepo, canonicalRepository, linkRepository, authorizationService,
                mock(com.taceiq.graph.service.GraphReadinessService.class), provenanceRepository, sourceRecordRepository
        );

        BatchConnectionMapResponse resp = service.getBatchConnectionMap(1005L);

        List<InvestigationPathDto> paths = resp.getPaths();
        assertNotNull(paths);
        // Both Machine path and Supplier path are distinct relationships and MUST remain!
        assertTrue(paths.stream().anyMatch(p -> p.getPath().contains("Machine:M-E")));
        assertTrue(paths.stream().anyMatch(p -> p.getPath().contains("Supplier:SUP-E")));
    }

    @Test
    @DisplayName("DATASET F: source files with missing optional fields -> graceful handling without crashes")
    void datasetF_missingOptionalFieldsGracefulHandling() {
        Investigation invF = Investigation.builder().id(1006L).organisation(org).investigationKey("INV-F").batchReference("BATCH-F").build();

        InvestigationRepository invRepo = mock(InvestigationRepository.class);
        when(invRepo.findByIdAndOrganisationOrgId(1006L, 10L)).thenReturn(Optional.of(invF));

        CanonicalEvidence sparseEv1 = CanonicalEvidence.builder().id(60L).organisation(org).incident(invF).externalId("SRC_MES_SPARSE").sourceType("MES").status("COMPLETED")
                .normalizedPayload("{\"batch_id\":\"BATCH-F\"}").lastSeenAt(Instant.now()).build();

        CanonicalEvidence sparseEv2 = CanonicalEvidence.builder().id(61L).organisation(org).incident(invF).externalId("SRC_LIMS_SPARSE").sourceType("LIMS").status(null)
                .normalizedPayload("{}").lastSeenAt(Instant.now()).build();

        when(canonicalRepository.findByIncidentIdAndOrganisationOrgId(1006L, 10L)).thenReturn(List.of(sparseEv1, sparseEv2));

        InvestigationEvidenceService service = new InvestigationEvidenceService(
                invRepo, canonicalRepository, linkRepository, authorizationService,
                mock(com.taceiq.graph.service.GraphReadinessService.class), provenanceRepository, sourceRecordRepository
        );

        assertDoesNotThrow(() -> {
            BatchConnectionMapResponse resp = service.getBatchConnectionMap(1006L);
            assertNotNull(resp);
            assertEquals("BATCH-F", resp.getBatchReference());
            assertTrue(resp.getCounts().get("machines") == 0);
            assertTrue(resp.getCounts().get("suppliers") == 0);
        });
    }

    @Test
    @DisplayName("DATASET G: file names and source record layouts supported by mapper -> identical generic semantic behavior")
    void datasetG_fileIndependenceAndMapperAliases() {
        SourceFieldMapper mapper = new SourceFieldMapper();

        // 1. Alternate filenames infer correct sourceType without hardcoded filename checks
        assertEquals("MES", mapper.inferSourceType("production_log.csv", null));
        assertEquals("LIMS", mapper.inferSourceType("quality_report.json", null));
        assertEquals("CMMS", mapper.inferSourceType("equipment_status.xlsx", null));
        assertEquals("SHIPMENT", mapper.inferSourceType("distribution_manifest.csv", null));

        // 2. Alternate field aliases extracted generically
        Map<String, String> row = Map.of(
                "lot_number", "LOT-999",
                "equipment_id", "EQ-55",
                "vendor_id", "VEN-77",
                "sku", "SKU-33"
        );

        Map<String, String> norm = mapper.extractNormalizedReferences(row);
        assertEquals("LOT-999", norm.get(SourceFieldMapper.BATCH));
        assertEquals("EQ-55", norm.get(SourceFieldMapper.MACHINE));
        assertEquals("VEN-77", norm.get(SourceFieldMapper.SUPPLIER));
        assertEquals("SKU-33", norm.get(SourceFieldMapper.PRODUCT));
    }
}
