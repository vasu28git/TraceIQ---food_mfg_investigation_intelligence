package com.taceiq;

import com.taceiq.dto.BatchConnectionMapResponse;
import com.taceiq.dto.InvestigationPathDto;
import com.taceiq.dto.InvestigationSignalDto;
import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.Investigation;
import com.taceiq.entity.Organisation;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.EvidenceCorrelationProvenanceRepository;
import com.taceiq.repository.InvestigationEvidenceRepository;
import com.taceiq.repository.InvestigationRepository;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.InvestigationEvidenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenericBatchConnectionMapTest {

    private InvestigationEvidenceService evidenceService;
    private CanonicalEvidenceRepository canonicalRepository;
    private InvestigationEvidenceRepository linkRepository;
    private EvidenceCorrelationProvenanceRepository provenanceRepository;
    private AuthorizationService authorizationService;

    private Organisation org;
    private Investigation genericInvestigation;

    @BeforeEach
    void setUp() {
        InvestigationRepository investigationRepository = mock(InvestigationRepository.class);
        canonicalRepository = mock(CanonicalEvidenceRepository.class);
        linkRepository = mock(InvestigationEvidenceRepository.class);
        provenanceRepository = mock(EvidenceCorrelationProvenanceRepository.class);
        authorizationService = mock(AuthorizationService.class);

        org = Organisation.builder().orgId(99L).name("GenericOrg").build();
        genericInvestigation = Investigation.builder()
                .id(200L)
                .organisation(org)
                .investigationKey("INV-GENERIC-99")
                .batchReference("BATCH-TEST-002")
                .productReference("PROD-ALPHA")
                .build();

        when(authorizationService.getCurrentOrgId()).thenReturn(99L);
        when(investigationRepository.findByIdAndOrganisationOrgId(200L, 99L)).thenReturn(Optional.of(genericInvestigation));
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
                provenanceRepository
        );
    }

    @Test
    void genericDataset_batchTest002_discoversAllEntitiesAndSignalsDynamically() {
        // Create canonical evidence for BATCH-TEST-002
        CanonicalEvidence qaEv = CanonicalEvidence.builder()
                .id(101L).organisation(org).incident(genericInvestigation)
                .externalId("SRC_LIMS_QA-9001").sourceType("LIMS").status("FAILED")
                .title("Seal Strength Test Failure")
                .normalizedPayload("{\"batch_id\":\"BATCH-TEST-002\",\"product_id\":\"PROD-ALPHA\",\"result\":\"FAILED\",\"measuredValue\":\"22.4 N/15mm\",\"minLimit\":\"35.0 N/15mm\"}")
                .lastSeenAt(Instant.now())
                .build();

        CanonicalEvidence cmmsEv = CanonicalEvidence.builder()
                .id(102L).organisation(org).incident(genericInvestigation)
                .externalId("SRC_CMMS_MAINT-5001").sourceType("CMMS").status("OVERDUE")
                .title("Overdue Maintenance on M-05")
                .normalizedPayload("{\"batch_id\":\"BATCH-TEST-002\",\"product_id\":\"PROD-ALPHA\",\"machine_id\":\"M-05\",\"maintenanceStatus\":\"OVERDUE\"}")
                .lastSeenAt(Instant.now())
                .build();

        CanonicalEvidence shipEv = CanonicalEvidence.builder()
                .id(103L).organisation(org).incident(genericInvestigation)
                .externalId("SRC_SHIP_9017").sourceType("SHIPMENT").status("RECALLED")
                .title("Recalled Transit Delivery")
                .normalizedPayload("{\"batch_id\":\"BATCH-TEST-002\",\"shipment_id\":\"SHIP-9017\",\"shipmentStatus\":\"RECALLED\"}")
                .lastSeenAt(Instant.now())
                .build();

        CanonicalEvidence whEv = CanonicalEvidence.builder()
                .id(104L).organisation(org).incident(genericInvestigation)
                .externalId("SRC_WMS_LOG-8023").sourceType("WAREHOUSE").status("READY")
                .title("Storage Log Zone WZ-B2")
                .normalizedPayload("{\"batch_id\":\"BATCH-TEST-002\",\"warehouse_zone\":\"WZ-B2\"}")
                .lastSeenAt(Instant.now())
                .build();

        CanonicalEvidence erpEv = CanonicalEvidence.builder()
                .id(105L).organisation(org).incident(genericInvestigation)
                .externalId("SRC_ERP_SUP-88").sourceType("ERP").status("READY")
                .title("Supplier Material Delivery")
                .normalizedPayload("{\"batch_id\":\"BATCH-TEST-002\",\"supplier_id\":\"SUP-88\"}")
                .lastSeenAt(Instant.now())
                .build();

        List<CanonicalEvidence> canonicalList = List.of(qaEv, cmmsEv, shipEv, whEv, erpEv);
        List<com.taceiq.entity.InvestigationEvidence> linkList = canonicalList.stream()
                .map(ce -> com.taceiq.entity.InvestigationEvidence.builder()
                        .id(ce.getId())
                        .organisation(org)
                        .investigation(genericInvestigation)
                        .canonicalEvidence(ce)
                        .createdAt(Instant.now())
                        .build())
                .collect(java.util.stream.Collectors.toList());

        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(anyLong(), anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(linkList));
        when(canonicalRepository.findByIncidentIdAndOrganisationOrgId(200L, 99L))
                .thenReturn(canonicalList);
        when(canonicalRepository.findAllByOrganisationOrgId(99L))
                .thenReturn(canonicalList);

        BatchConnectionMapResponse mapResponse = evidenceService.getBatchConnectionMap(200L);

        // 1. Batch is Depth 0 Root (no Incident prefix in graph)
        assertEquals("BATCH-TEST-002", mapResponse.getBatchReference());
        assertTrue(mapResponse.getNodes().stream().anyMatch(n -> "Batch".equals(n.getType()) && "BATCH-TEST-002".equals(n.getStableId())));
        assertFalse(mapResponse.getNodes().stream().anyMatch(n -> n.getStableId().startsWith("Incident:")));

        // 2. All distinct entity types discovered dynamically
        assertTrue(mapResponse.getCounts().get("products") >= 1);
        assertTrue(mapResponse.getCounts().get("machines") >= 1);
        assertTrue(mapResponse.getCounts().get("suppliers") >= 1);
        assertTrue(mapResponse.getCounts().get("shipments") >= 1);
        assertTrue(mapResponse.getCounts().get("warehouseRecords") >= 1);
        assertTrue(mapResponse.getCounts().get("qaLimsRecords") >= 1);
        assertEquals(5, mapResponse.getCounts().get("evidenceRecords"));

        // 3. Structured Signal Extraction (QA FAILED, Maintenance OVERDUE, Shipment RECALLED, Out-of-spec measuredValue)
        List<InvestigationSignalDto> signals = mapResponse.getSignals();
        assertNotNull(signals);
        assertFalse(signals.isEmpty());

        InvestigationSignalDto qaSignal = signals.stream()
                .filter(s -> "SRC_LIMS_QA-9001".equals(s.getSourceEvidenceId()))
                .findFirst().orElse(null);
        assertNotNull(qaSignal);
        assertEquals("CRITICAL", qaSignal.getSignalType());
        assertEquals("FAILED", qaSignal.getActualValue());
        assertEquals("PASS", qaSignal.getExpectedValue());
        assertTrue(qaSignal.getDeterministicReason().contains("failed status"));

        InvestigationSignalDto maintSignal = signals.stream()
                .filter(s -> "SRC_CMMS_MAINT-5001".equals(s.getSourceEvidenceId()))
                .findFirst().orElse(null);
        assertNotNull(maintSignal);
        assertEquals("WARNING", maintSignal.getSignalType());
        assertEquals("OVERDUE", maintSignal.getActualValue());
        assertEquals("maintenanceStatus", maintSignal.getExactField());

        // 4. Investigation Paths prioritized dynamically
        List<InvestigationPathDto> paths = mapResponse.getPaths();
        assertNotNull(paths);
        assertFalse(paths.isEmpty());
        assertEquals("HIGH", paths.get(0).getPriority());

        // 5. Non-causal terminology check
        for (InvestigationSignalDto s : signals) {
            String r = s.getDeterministicReason().toLowerCase();
            assertFalse(r.contains("caused"));
            assertFalse(r.contains("resulted in"));
            assertFalse(r.contains("responsible for"));
        }
    }

    @Test
    void genericDataset_batchTest003_completelyDifferentStructure_adaptsAutomatically() {
        Investigation inv3 = Investigation.builder()
                .id(300L)
                .organisation(org)
                .investigationKey("INV-GENERIC-303")
                .batchReference("BATCH-TEST-003")
                .productReference("PROD-GAMMA")
                .build();

        when(authorizationService.getCurrentOrgId()).thenReturn(99L);
        when(canonicalRepository.findByExternalIdAndOrganisationOrgId("QA-3001", 99L)).thenReturn(Optional.empty());

        CanonicalEvidence qaEv1 = CanonicalEvidence.builder()
                .id(301L).organisation(org).incident(inv3)
                .externalId("SRC_LIMS_QA-3001").sourceType("LIMS").status("FAILED")
                .title("Viscosity Spec Failure")
                .normalizedPayload("{\"batch_id\":\"BATCH-TEST-003\",\"product_id\":\"PROD-GAMMA\",\"result\":\"FAILED\",\"sample_id\":\"QA-3001\"}")
                .lastSeenAt(Instant.now())
                .build();

        CanonicalEvidence cmmsEv1 = CanonicalEvidence.builder()
                .id(302L).organisation(org).incident(inv3)
                .externalId("SRC_CMMS_MAINT-9901").sourceType("CMMS").status("OVERDUE")
                .title("Overdue Calibration M-12")
                .normalizedPayload("{\"batch_id\":\"BATCH-TEST-003\",\"product_id\":\"PROD-GAMMA\",\"machine_id\":\"M-12\",\"maintenanceStatus\":\"OVERDUE\"}")
                .lastSeenAt(Instant.now())
                .build();

        CanonicalEvidence cmmsEv2 = CanonicalEvidence.builder()
                .id(303L).organisation(org).incident(inv3)
                .externalId("SRC_CMMS_MAINT-9902").sourceType("CMMS").status("READY")
                .title("Inspection M-14")
                .normalizedPayload("{\"batch_id\":\"BATCH-TEST-003\",\"product_id\":\"PROD-GAMMA\",\"machine_id\":\"M-14\"}")
                .lastSeenAt(Instant.now())
                .build();

        CanonicalEvidence shipEv1 = CanonicalEvidence.builder()
                .id(304L).organisation(org).incident(inv3)
                .externalId("SRC_SHIP_4099").sourceType("SHIPMENT").status("RECALLED")
                .title("Recalled Shipment 4099")
                .normalizedPayload("{\"batch_id\":\"BATCH-TEST-003\",\"shipment_id\":\"SHIP-4099\",\"customer_id\":\"CUST-999\",\"shipmentStatus\":\"RECALLED\"}")
                .lastSeenAt(Instant.now())
                .build();

        CanonicalEvidence whEv1 = CanonicalEvidence.builder()
                .id(305L).organisation(org).incident(inv3)
                .externalId("SRC_WMS_LOG-100").sourceType("WAREHOUSE").status("READY")
                .title("Warehouse Zone C3")
                .normalizedPayload("{\"batch_id\":\"BATCH-TEST-003\",\"warehouse_zone\":\"WZ-C3\"}")
                .lastSeenAt(Instant.now())
                .build();

        CanonicalEvidence whEv2 = CanonicalEvidence.builder()
                .id(306L).organisation(org).incident(inv3)
                .externalId("SRC_WMS_LOG-101").sourceType("WAREHOUSE").status("READY")
                .title("Warehouse Zone C4")
                .normalizedPayload("{\"batch_id\":\"BATCH-TEST-003\",\"warehouse_zone\":\"WZ-C4\"}")
                .lastSeenAt(Instant.now())
                .build();

        CanonicalEvidence erpEv1 = CanonicalEvidence.builder()
                .id(307L).organisation(org).incident(inv3)
                .externalId("SRC_ERP_SUP-77").sourceType("ERP").status("READY")
                .title("Supplier Delivery SUP-77")
                .normalizedPayload("{\"batch_id\":\"BATCH-TEST-003\",\"supplier_id\":\"SUP-77\"}")
                .lastSeenAt(Instant.now())
                .build();

        List<CanonicalEvidence> canonicalList3 = List.of(qaEv1, cmmsEv1, cmmsEv2, shipEv1, whEv1, whEv2, erpEv1);
        List<com.taceiq.entity.InvestigationEvidence> linkList3 = canonicalList3.stream()
                .map(ce -> com.taceiq.entity.InvestigationEvidence.builder()
                        .id(ce.getId())
                        .organisation(org)
                        .investigation(inv3)
                        .canonicalEvidence(ce)
                        .createdAt(Instant.now())
                        .build())
                .collect(java.util.stream.Collectors.toList());

        InvestigationRepository invRepoMock = mock(InvestigationRepository.class);
        when(invRepoMock.findByIdAndOrganisationOrgId(300L, 99L)).thenReturn(Optional.of(inv3));
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(99L, 300L, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(linkList3));
        when(canonicalRepository.findByIncidentIdAndOrganisationOrgId(300L, 99L))
                .thenReturn(canonicalList3);
        when(canonicalRepository.findAllByOrganisationOrgId(99L))
                .thenReturn(canonicalList3);

        InvestigationEvidenceService service3 = new InvestigationEvidenceService(
                invRepoMock,
                canonicalRepository,
                linkRepository,
                authorizationService,
                mock(com.taceiq.graph.service.GraphReadinessService.class),
                provenanceRepository
        );

        BatchConnectionMapResponse response3 = service3.getBatchConnectionMap(300L);

        // 1. Root batch is BATCH-TEST-003, no Incident node
        assertEquals("BATCH-TEST-003", response3.getBatchReference());
        assertFalse(response3.getNodes().stream().anyMatch(n -> n.getStableId().startsWith("Incident:")));

        // 2. Counts adapt dynamically to dataset 3
        assertEquals(1, response3.getCounts().get("products"));
        assertEquals(2, response3.getCounts().get("machines")); // M-12, M-14
        assertEquals(2, response3.getCounts().get("warehouseRecords")); // WZ-C3, WZ-C4
        assertEquals(1, response3.getCounts().get("shipments")); // SHIP-4099
        assertEquals(1, response3.getCounts().get("suppliers")); // SUP-77
        assertEquals(1, response3.getCounts().get("qaLimsRecords")); // QA-3001
        assertEquals(7, response3.getCounts().get("evidenceRecords"));

        // 3. Customer CUST-999 node present under Shipment SHIP-4099
        assertTrue(response3.getNodes().stream().anyMatch(n -> "Customer".equals(n.getType()) && "CUST-999".equals(n.getStableId())));

        // 4. Stable ID lookup works for both externalId and sourceRecordId format without error
        var detail1 = service3.getEvidenceDetail(300L, "SRC_LIMS_QA-3001");
        assertNotNull(detail1);
        assertEquals("SRC_LIMS_QA-3001", detail1.getStableId());

        var detail2 = service3.getEvidenceDetail(300L, "QA-3001");
        assertNotNull(detail2);
        assertEquals("SRC_LIMS_QA-3001", detail2.getStableId());
    }

    @Test
    void negativeTest_productTitlesDoNotGenerateFalseSignals() {
        CanonicalEvidence prodEv1 = CanonicalEvidence.builder()
                .id(401L).organisation(org).incident(genericInvestigation)
                .externalId("SRC_ERP_PROD_1").sourceType("ERP").status("COMPLETED")
                .title("Classic Ranch Salad Dressing 355ml")
                .normalizedPayload("{\"batch_id\":\"BATCH-TEST-002\",\"product_name\":\"Classic Ranch Salad Dressing 355ml\",\"notes\":\"Normal production run\"}")
                .lastSeenAt(Instant.now())
                .build();

        CanonicalEvidence prodEv2 = CanonicalEvidence.builder()
                .id(402L).organisation(org).incident(genericInvestigation)
                .externalId("SRC_ERP_PROD_2").sourceType("ERP").status("READY")
                .title("Honey Oat Granola Bars 12pk")
                .normalizedPayload("{\"batch_id\":\"BATCH-TEST-002\",\"product_name\":\"Honey Oat Granola Bars 12pk\"}")
                .lastSeenAt(Instant.now())
                .build();

        CanonicalEvidence prodEv3 = CanonicalEvidence.builder()
                .id(403L).organisation(org).incident(genericInvestigation)
                .externalId("SRC_ERP_PROD_3").sourceType("ERP").status("PASS")
                .title("BBQ Seasoned Beef Jerky 85g")
                .normalizedPayload("{\"batch_id\":\"BATCH-TEST-002\",\"product_name\":\"BBQ Seasoned Beef Jerky 85g\"}")
                .lastSeenAt(Instant.now())
                .build();

        List<CanonicalEvidence> canonicalList = List.of(prodEv1, prodEv2, prodEv3);
        List<com.taceiq.entity.InvestigationEvidence> linkList = canonicalList.stream()
                .map(ce -> com.taceiq.entity.InvestigationEvidence.builder()
                        .id(ce.getId())
                        .organisation(org)
                        .investigation(genericInvestigation)
                        .canonicalEvidence(ce)
                        .createdAt(Instant.now())
                        .build())
                .collect(java.util.stream.Collectors.toList());

        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(anyLong(), anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(linkList));
        when(canonicalRepository.findByIncidentIdAndOrganisationOrgId(200L, 99L))
                .thenReturn(canonicalList);
        when(canonicalRepository.findAllByOrganisationOrgId(99L))
                .thenReturn(canonicalList);

        BatchConnectionMapResponse response = evidenceService.getBatchConnectionMap(200L);

        // Explicit assertion: Ordinary product titles must NEVER generate warning or critical signals!
        assertTrue(response.getSignals().isEmpty(), "Ordinary product titles must NOT generate signals!");
    }

    @Test
    void negativeTest_unrelatedOrgWideRecordsAreExcludedFromBatchGraph() {
        // Connected evidence for BATCH-TEST-002
        CanonicalEvidence connEv = CanonicalEvidence.builder()
                .id(501L).organisation(org).incident(genericInvestigation)
                .externalId("SRC_MES_100").sourceType("MES").status("COMPLETED")
                .title("Production Record M-02")
                .normalizedPayload("{\"batch_id\":\"BATCH-TEST-002\",\"product_id\":\"PROD-ALPHA\",\"machine_id\":\"M-02\"}")
                .lastSeenAt(Instant.now())
                .build();

        // Unrelated evidence belonging to another batch BATCH-OTHER-999
        CanonicalEvidence unrelatedEv = CanonicalEvidence.builder()
                .id(502L).organisation(org)
                .externalId("SRC_CMMS_OTHER").sourceType("CMMS").status("OVERDUE")
                .title("Overdue Maintenance on M-99")
                .normalizedPayload("{\"batch_id\":\"BATCH-OTHER-999\",\"product_id\":\"PROD-UNRELATED\",\"machine_id\":\"M-99\",\"supplier_id\":\"SUP-999\"}")
                .lastSeenAt(Instant.now())
                .build();

        List<CanonicalEvidence> batchEvidence = List.of(connEv);
        List<CanonicalEvidence> allOrgEvidence = List.of(connEv, unrelatedEv);

        List<com.taceiq.entity.InvestigationEvidence> linkList = List.of(
                com.taceiq.entity.InvestigationEvidence.builder()
                        .id(501L).organisation(org).investigation(genericInvestigation)
                        .canonicalEvidence(connEv).createdAt(Instant.now()).build()
        );

        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(anyLong(), anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(linkList));
        when(canonicalRepository.findByIncidentIdAndOrganisationOrgId(200L, 99L))
                .thenReturn(batchEvidence);
        when(canonicalRepository.findAllByOrganisationOrgId(99L))
                .thenReturn(allOrgEvidence);

        BatchConnectionMapResponse response = evidenceService.getBatchConnectionMap(200L);

        // Assert unrelated machine M-99, unrelated product PROD-UNRELATED, and unrelated supplier SUP-999 are NOT present
        assertFalse(response.getNodes().stream().anyMatch(n -> "M-99".equals(n.getStableId())));
        assertFalse(response.getNodes().stream().anyMatch(n -> "PROD-UNRELATED".equals(n.getStableId())));
        assertFalse(response.getNodes().stream().anyMatch(n -> "SUP-999".equals(n.getStableId())));
        assertFalse(response.getNodes().stream().anyMatch(n -> "SRC_CMMS_OTHER".equals(n.getStableId())));
    }
}
