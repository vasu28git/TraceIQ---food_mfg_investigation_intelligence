package com.taceiq;

import com.taceiq.dto.BatchConnectionMapResponse;
import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.IngestedSourceRecord;
import com.taceiq.entity.Investigation;
import com.taceiq.entity.Organisation;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.EvidenceCorrelationProvenanceRepository;
import com.taceiq.repository.IngestedSourceRecordRepository;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for BATCH-1010 over-correlation.
 * BATCH-1010 -> PRD-106 -> M-05
 * BATCH-1019 -> PRD-106 -> M-07
 * M-07 must be CROSS_BATCH_CONTEXT for BATCH-1010, not PRIMARY.
 * Signals from BATCH-1019 must not appear in BATCH-1010's signal list.
 */
class Batch1010CrossBatchRegressionTest {

    private InvestigationEvidenceService evidenceService;
    private CanonicalEvidenceRepository canonicalRepository;
    private InvestigationEvidenceRepository linkRepository;
    private EvidenceCorrelationProvenanceRepository provenanceRepository;
    private IngestedSourceRecordRepository sourceRecordRepository;
    private AuthorizationService authorizationService;
    private Organisation org;
    private Investigation batch1010Investigation;

    @BeforeEach
    void setUp() {
        InvestigationRepository investigationRepository = mock(InvestigationRepository.class);
        canonicalRepository = mock(CanonicalEvidenceRepository.class);
        linkRepository = mock(InvestigationEvidenceRepository.class);
        provenanceRepository = mock(EvidenceCorrelationProvenanceRepository.class);
        sourceRecordRepository = mock(IngestedSourceRecordRepository.class);
        authorizationService = mock(AuthorizationService.class);

        org = Organisation.builder().orgId(1L).name("TestOrg").build();
        batch1010Investigation = Investigation.builder()
                .id(1010L)
                .organisation(org)
                .investigationKey("INV-1010")
                .batchReference("BATCH-1010")
                .productReference("PRD-106")
                .build();

        when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        when(investigationRepository.findByIdAndOrganisationOrgId(1010L, 1L)).thenReturn(Optional.of(batch1010Investigation));
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
    void batch1010_m07_isCrossBatch_notPrimary() {
        // Direct MES record for BATCH-1010 with M-05
        CanonicalEvidence mes1010 = CanonicalEvidence.builder()
                .id(1L).organisation(org).incident(batch1010Investigation)
                .externalId("SRC_MES_BATCH-1010").sourceType("MES").status("COMPLETED")
                .title("MES BATCH-1010")
                .normalizedPayload("{\"batch_id\":\"BATCH-1010\",\"product_id\":\"PRD-106\",\"machine_id\":\"M-05\",\"supplier_id\":\"SUP-09\"}")
                .lastSeenAt(Instant.now())
                .build();

        // QA evidence for BATCH-1010
        CanonicalEvidence qa2001 = CanonicalEvidence.builder()
                .id(2L).organisation(org).incident(batch1010Investigation)
                .externalId("SRC_LIMS_QA-2001").sourceType("LIMS").status("FAILED")
                .title("QA-2001 Seal Strength FAILED 26.91 N/15mm")
                .normalizedPayload("{\"batch_id\":\"BATCH-1010\",\"sample_id\":\"QA-2001\",\"result\":\"FAILED\",\"measuredValue\":\"26.91 N/15mm\",\"minLimit\":\"28\",\"maxLimit\":\"45\"}")
                .lastSeenAt(Instant.now())
                .build();

        // PKG-401 for BATCH-1010
        CanonicalEvidence pkg401 = CanonicalEvidence.builder()
                .id(3L).organisation(org).incident(batch1010Investigation)
                .externalId("SRC_PKG_PKG-401").sourceType("LIMS").status("FAILED")
                .title("PKG-401 packaging FAILED")
                .normalizedPayload("{\"batch_id\":\"BATCH-1010\",\"sample_id\":\"PKG-401\",\"result\":\"FAILED\"}")
                .lastSeenAt(Instant.now())
                .build();

        // SHIP-2017 for BATCH-1010 with customer
        CanonicalEvidence ship2017 = CanonicalEvidence.builder()
                .id(4L).organisation(org).incident(batch1010Investigation)
                .externalId("SRC_SHIP_SHIP-2017").sourceType("SHIPMENT").status("RECALLED")
                .title("SHIP-2017 Recalled")
                .normalizedPayload("{\"batch_id\":\"BATCH-1010\",\"shipment_id\":\"SHIP-2017\",\"customer_id\":\"CUST-1096\",\"shipmentStatus\":\"RECALLED\"}")
                .lastSeenAt(Instant.now())
                .build();

        // Maintenance for M-05 (PRIMARY - directly used by BATCH-1010)
        CanonicalEvidence maint1014 = CanonicalEvidence.builder()
                .id(5L).organisation(org).incident(batch1010Investigation)
                .externalId("SRC_CMMS_MAINT-1014").sourceType("CMMS").status("OVERDUE")
                .title("MAINT-1014 OVERDUE M-05")
                .normalizedPayload("{\"machine_id\":\"M-05\",\"maintenanceStatus\":\"OVERDUE\"}")
                .lastSeenAt(Instant.now())
                .build();

        CanonicalEvidence maint1020 = CanonicalEvidence.builder()
                .id(6L).organisation(org).incident(batch1010Investigation)
                .externalId("SRC_CMMS_MAINT-1020").sourceType("CMMS").status("OVERDUE")
                .title("MAINT-1020 Bearing vibration M-05")
                .normalizedPayload("{\"machine_id\":\"M-05\",\"maintenanceStatus\":\"OVERDUE\",\"notes\":\"Bearing vibration observed\"}")
                .lastSeenAt(Instant.now())
                .build();

        // Cross-batch evidence: MAINT for M-07 (belongs to BATCH-1019 via PRD-106, not BATCH-1010)
        // This simulates the over-correlation bug: product PRD-106 -> machine M-07 -> maintenance
        CanonicalEvidence maintM07 = CanonicalEvidence.builder()
                .id(7L).organisation(org).incident(batch1010Investigation)
                .externalId("SRC_CMMS_MAINT-M07").sourceType("CMMS").status("OVERDUE")
                .title("MAINT for M-07")
                .normalizedPayload("{\"machine_id\":\"M-07\",\"maintenanceStatus\":\"OVERDUE\"}")
                .lastSeenAt(Instant.now())
                .build();

        // LOG records for BATCH-1010
        CanonicalEvidence log3023 = CanonicalEvidence.builder()
                .id(8L).organisation(org).incident(batch1010Investigation)
                .externalId("SRC_WMS_LOG-3023").sourceType("WAREHOUSE").status("READY")
                .title("LOG-3023")
                .normalizedPayload("{\"batch_id\":\"BATCH-1010\",\"warehouse_zone\":\"Zone-A\"}")
                .lastSeenAt(Instant.now())
                .build();

        List<CanonicalEvidence> primaryList = List.of(mes1010, qa2001, pkg401, ship2017, maint1014, maint1020, log3023);
        List<CanonicalEvidence> allList = List.of(mes1010, qa2001, pkg401, ship2017, maint1014, maint1020, log3023, maintM07);

        List<com.taceiq.entity.InvestigationEvidence> linkList = allList.stream()
                .map(ce -> com.taceiq.entity.InvestigationEvidence.builder()
                        .id(ce.getId()).organisation(org).investigation(batch1010Investigation).canonicalEvidence(ce).createdAt(Instant.now()).build())
                .toList();

        when(canonicalRepository.findByIncidentIdAndOrganisationOrgId(1010L, 1L)).thenReturn(allList);
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(anyLong(), anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(linkList));
        when(canonicalRepository.findAllByOrganisationOrgId(1L)).thenReturn(allList);

        // Mock source records for related batch discovery: BATCH-1019 uses PRD-106 and M-07
        IngestedSourceRecord rec1019 = IngestedSourceRecord.builder()
                .id(100L).organisation(org).sourceType("MES").sourceRecordId("BATCH-1019")
                .batchReference("BATCH-1019").productReference("PRD-106").machineReference("M-07")
                .payload("{}").ingestedAt(Instant.now()).build();
        IngestedSourceRecord rec1010 = IngestedSourceRecord.builder()
                .id(101L).organisation(org).sourceType("MES").sourceRecordId("BATCH-1010")
                .batchReference("BATCH-1010").productReference("PRD-106").machineReference("M-05")
                .payload("{}").ingestedAt(Instant.now()).build();
        when(sourceRecordRepository.findByOrganisationOrgIdAndProductReference(1L, "PRD-106"))
                .thenReturn(List.of(rec1010, rec1019));
        when(sourceRecordRepository.countByOrganisationOrgIdAndBatchReference(1L, "BATCH-1019")).thenReturn(5L);
        when(sourceRecordRepository.countByOrganisationOrgIdAndBatchReference(1L, "BATCH-1010")).thenReturn(7L);

        BatchConnectionMapResponse response = evidenceService.getBatchConnectionMap(1010L);

        // PRIMARY assertions
        assertEquals("BATCH-1010", response.getBatchReference());
        // M-05 must be PRIMARY
        assertTrue(response.getNodes().stream().anyMatch(n -> "Machine".equals(n.getType()) && "M-05".equals(n.getStableId())),
                "M-05 should be PRIMARY for BATCH-1010");
        // M-07 must NOT be in PRIMARY nodes
        assertFalse(response.getNodes().stream().anyMatch(n -> "Machine".equals(n.getType()) && "M-07".equals(n.getStableId())),
                "M-07 must NOT be PRIMARY for BATCH-1010 - it crosses via BATCH-1019");
        // M-07 must be in cross-batch context
        assertNotNull(response.getCrossBatchNodes());
        assertTrue(response.getCrossBatchNodes().stream().anyMatch(n -> "M-07".equals(n.getStableId())),
                "M-07 should appear in crossBatchNodes");
        // Related batch must contain BATCH-1019
        assertNotNull(response.getRelatedBatches());
        assertTrue(response.getRelatedBatches().stream().anyMatch(rb -> "BATCH-1019".equals(rb.getBatchReference())),
                "BATCH-1019 should be listed as related batch sharing PRD-106");
        // Primary evidence count must NOT include M-07 maintenance
        assertEquals(7, response.getCounts().get("evidenceRecords"), "Primary evidence count should be 7, not 8");
        assertEquals(1, response.getCrossBatchCounts().get("evidenceRecords"), "Cross-batch evidence count should be 1 (M-07)");
        // Primary should have only 1 machine (M-05)
        assertEquals(1, response.getCounts().get("machines"), "Primary machines should be 1 (M-05 only)");
        // Paths must not contain M-07
        assertTrue(response.getPaths().stream().noneMatch(p -> p.getPath().stream().anyMatch(tok -> tok.contains("M-07"))),
                "No PRIMARY investigation path should contain M-07");
        // SHIP-2017 explanation must be DIRECT_BATCH_MATCH path Batch->Shipment->Customer
        // Check that shipment node exists and customer exists in primary graph
        assertTrue(response.getNodes().stream().anyMatch(n -> "Shipment".equals(n.getType()) && "SHIP-2017".equals(n.getStableId())));
        assertTrue(response.getNodes().stream().anyMatch(n -> "Customer".equals(n.getType()) && "CUST-1096".equals(n.getStableId())));
    }

    @Test
    void signalsFromBatch1019_notInBatch1010() {
        CanonicalEvidence mes1010 = CanonicalEvidence.builder()
                .id(10L).organisation(org).incident(batch1010Investigation)
                .externalId("SRC_MES_B1010").sourceType("MES").status("COMPLETED")
                .title("MES BATCH-1010")
                .normalizedPayload("{\"batch_id\":\"BATCH-1010\",\"product_id\":\"PRD-106\",\"machine_id\":\"M-05\"}")
                .lastSeenAt(Instant.now())
                .build();

        // Primary QA for BATCH-1010
        CanonicalEvidence qa1010 = CanonicalEvidence.builder()
                .id(11L).organisation(org).incident(batch1010Investigation)
                .externalId("SRC_LIMS_QA-2001").sourceType("LIMS").status("FAILED")
                .title("QA-2001 FAILED for BATCH-1010")
                .normalizedPayload("{\"batch_id\":\"BATCH-1010\",\"sample_id\":\"QA-2001\",\"result\":\"FAILED\"}")
                .lastSeenAt(Instant.now())
                .build();

        // Cross-batch QA for BATCH-1019 (should be CROSS_BATCH)
        CanonicalEvidence qa1019 = CanonicalEvidence.builder()
                .id(12L).organisation(org).incident(batch1010Investigation)
                .externalId("SRC_LIMS_QA-1019").sourceType("LIMS").status("FAILED")
                .title("QA for BATCH-1019")
                .normalizedPayload("{\"batch_id\":\"BATCH-1019\",\"sample_id\":\"QA-1019\",\"result\":\"FAILED\",\"product_id\":\"PRD-106\"}")
                .lastSeenAt(Instant.now())
                .build();

        List<CanonicalEvidence> all = List.of(mes1010, qa1010, qa1019);
        List<com.taceiq.entity.InvestigationEvidence> links = all.stream()
                .map(ce -> com.taceiq.entity.InvestigationEvidence.builder()
                        .id(ce.getId()).organisation(org).investigation(batch1010Investigation).canonicalEvidence(ce).createdAt(Instant.now()).build())
                .toList();
        when(canonicalRepository.findByIncidentIdAndOrganisationOrgId(1010L, 1L)).thenReturn(all);
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(anyLong(), anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(links));
        when(canonicalRepository.findAllByOrganisationOrgId(1L)).thenReturn(all);
        when(sourceRecordRepository.findByOrganisationOrgIdAndProductReference(anyLong(), anyString())).thenReturn(List.of());

        BatchConnectionMapResponse response = evidenceService.getBatchConnectionMap(1010L);

        // Only QA-2001 signal should be present, not QA-1019
        assertFalse(response.getSignals().isEmpty(), "Should have at least one signal from BATCH-1010");
        assertTrue(response.getSignals().stream().anyMatch(s -> "SRC_LIMS_QA-2001".equals(s.getSourceEvidenceId())),
                "Signal from QA-2001 (BATCH-1010) must be present");
        assertTrue(response.getSignals().stream().noneMatch(s -> "SRC_LIMS_QA-1019".equals(s.getSourceEvidenceId())),
                "Signal from BATCH-1019 must NOT appear in BATCH-1010 signal list");
        // Cross-batch evidence should contain QA-1019
        assertTrue(response.getCrossBatchEvidence().stream().anyMatch(e -> "SRC_LIMS_QA-1019".equals(e.getStableId())),
                "QA-1019 should be in crossBatchEvidence");
        // Primary evidence should not contain QA-1019
        assertTrue(response.getEvidence().stream().noneMatch(e -> "SRC_LIMS_QA-1019".equals(e.getStableId())),
                "QA-1019 should NOT be in primary evidence");
    }
}
