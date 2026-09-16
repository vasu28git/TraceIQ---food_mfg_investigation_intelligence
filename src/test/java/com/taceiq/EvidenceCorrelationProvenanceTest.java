package com.taceiq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.EvidenceCorrelationProvenance;
import com.taceiq.entity.IngestedSourceRecord;
import com.taceiq.entity.Investigation;
import com.taceiq.entity.Organisation;
import com.taceiq.graph.service.GraphProjectionService;
import com.taceiq.ingestion.EvidenceDiscoveryService;
import com.taceiq.ingestion.SourceFieldMapper;
import com.taceiq.ingestion.SourceRecordIngestionService;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.EvidenceCorrelationProvenanceRepository;
import com.taceiq.repository.IngestedSourceRecordRepository;
import com.taceiq.repository.InvestigationEvidenceRepository;
import com.taceiq.repository.InvestigationRepository;
import com.taceiq.repository.OrganisationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvidenceCorrelationProvenanceTest {

    private IngestedSourceRecordRepository sourceRepository;
    private CanonicalEvidenceRepository canonicalRepository;
    private EvidenceCorrelationProvenanceRepository provenanceRepository;
    private Organisation organisation;
    private Investigation investigation;
    private EvidenceDiscoveryService discoveryService;

    @BeforeEach
    void setUp() {
        InvestigationRepository investigationRepository = mock(InvestigationRepository.class);
        sourceRepository = mock(IngestedSourceRecordRepository.class);
        canonicalRepository = mock(CanonicalEvidenceRepository.class);
        OrganisationRepository organisationRepository = mock(OrganisationRepository.class);
        GraphProjectionService graphProjectionService = mock(GraphProjectionService.class);
        SourceRecordIngestionService ingestionService = mock(SourceRecordIngestionService.class);
        InvestigationEvidenceRepository investigationEvidenceRepository = mock(InvestigationEvidenceRepository.class);
        provenanceRepository = mock(EvidenceCorrelationProvenanceRepository.class);

        organisation = Organisation.builder().orgId(1L).name("Org").build();
        investigation = Investigation.builder().id(10L).organisation(organisation).investigationKey("INV-10")
                .title("Incident").batchReference("BATCH-1010").build();

        when(investigationRepository.findByIdAndOrganisationOrgId(10L, 1L)).thenReturn(Optional.of(investigation));
        when(sourceRepository.countByOrganisationOrgId(1L)).thenReturn(2L);
        when(organisationRepository.getReferenceById(1L)).thenReturn(organisation);
        when(provenanceRepository.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(anyLong(), anyLong(), anyLong()))
                .thenReturn(List.of());
        when(canonicalRepository.findByExternalIdAndOrganisationOrgId(any(), anyLong())).thenReturn(Optional.empty());
        when(canonicalRepository.save(any(CanonicalEvidence.class))).thenAnswer(invocation -> {
            CanonicalEvidence evidence = invocation.getArgument(0);
            if (evidence.getId() == null) evidence.setId((long) (100 + Math.abs(evidence.getExternalId().hashCode() % 100)));
            return evidence;
        });

        discoveryService = new EvidenceDiscoveryService(
                investigationRepository, sourceRepository, canonicalRepository, organisationRepository,
                graphProjectionService, ingestionService, new SourceFieldMapper(), new ObjectMapper(),
                investigationEvidenceRepository, provenanceRepository);
    }

    @Test
    void storesDirectBatchAndMachineSecondHopExplanations() {
        IngestedSourceRecord direct = record("MES", "MES-1", "{\"batch_id\":\"BATCH-1010\",\"machine_id\":\"M-05\"}", "BATCH-1010", "M-05", null, null);
        IngestedSourceRecord machine = record("CMMS", "MAINT-1020", "{\"machine_id\":\"M-05\",\"record_id\":\"MAINT-1020\"}", null, "M-05", null, null);
        when(sourceRepository.findByOrganisationOrgIdAndBatchReference(1L, "BATCH-1010")).thenReturn(List.of(direct));
        when(sourceRepository.findByOrganisationOrgIdAndMachineReference(1L, "M-05")).thenReturn(List.of(direct, machine));
        when(sourceRepository.findByOrganisationOrgIdAndSupplierReference(anyLong(), any())).thenReturn(List.of());
        when(sourceRepository.findByOrganisationOrgIdAndProductReference(anyLong(), any())).thenReturn(List.of());

        discoveryService.discoverForIncident(10L, 1L);

        var captured = org.mockito.ArgumentCaptor.forClass(EvidenceCorrelationProvenance.class);
        org.mockito.Mockito.verify(provenanceRepository, org.mockito.Mockito.times(3)).save(captured.capture());
        List<EvidenceCorrelationProvenance> explanations = captured.getAllValues();
        assertTrue(explanations.stream().anyMatch(p -> "DIRECT_BATCH_MATCH".equals(p.getReason())
                && "batch_id".equals(p.getMatchedField())
                && "BATCH-1010".equals(p.getMatchedValue())
                && p.getConnectionPath().contains("Batch:BATCH-1010")));
        assertTrue(explanations.stream().anyMatch(p -> "MACHINE_MATCH_FROM_BATCH".equals(p.getReason())
                && "machine_id".equals(p.getMatchedField())
                && "M-05".equals(p.getMatchedValue())
                && p.getConnectionPath().contains("Machine:M-05")));
        assertEquals(3, explanations.size());
    }

    @Test
    void storesMultipleMachinesViaProductAndPreservesIndirectSemantics() {
        IngestedSourceRecord direct = record("MES", "BATCH-1010", "{\"batch_id\":\"BATCH-1010\",\"machine_id\":\"M-05\",\"product_id\":\"PRD-106\"}", "BATCH-1010", "M-05", null, "PRD-106");
        IngestedSourceRecord prodMesM06 = record("MES", "BATCH-1028", "{\"batch_id\":\"BATCH-1028\",\"machine_id\":\"M-06\",\"product_id\":\"PRD-106\"}", "BATCH-1028", "M-06", null, "PRD-106");
        IngestedSourceRecord cmmsM05 = record("CMMS", "MAINT-1020", "{\"machine_id\":\"M-05\",\"record_id\":\"MAINT-1020\"}", null, "M-05", null, null);
        IngestedSourceRecord cmmsM06 = record("CMMS", "MAINT-1022", "{\"machine_id\":\"M-06\",\"record_id\":\"MAINT-1022\"}", null, "M-06", null, null);

        when(sourceRepository.findByOrganisationOrgIdAndBatchReference(1L, "BATCH-1010")).thenReturn(List.of(direct));
        when(sourceRepository.findByOrganisationOrgIdAndProductReference(1L, "PRD-106")).thenReturn(List.of(direct, prodMesM06));
        when(sourceRepository.findByOrganisationOrgIdAndMachineReference(1L, "M-05")).thenReturn(List.of(direct, cmmsM05));
        when(sourceRepository.findByOrganisationOrgIdAndMachineReference(1L, "M-06")).thenReturn(List.of(cmmsM06));
        when(sourceRepository.findByOrganisationOrgIdAndSupplierReference(anyLong(), any())).thenReturn(List.of());

        discoveryService.discoverForIncident(10L, 1L);

        var captured = org.mockito.ArgumentCaptor.forClass(EvidenceCorrelationProvenance.class);
        org.mockito.Mockito.verify(provenanceRepository, org.mockito.Mockito.atLeastOnce()).save(captured.capture());
        List<EvidenceCorrelationProvenance> explanations = captured.getAllValues();

        // M-05 was in direct batch MES record, so it has direct MACHINE_MATCH_FROM_BATCH
        assertTrue(explanations.stream().anyMatch(p -> "MACHINE_MATCH_FROM_BATCH".equals(p.getReason())
                && "M-05".equals(p.getIntermediateEntityValue())
                && p.getConnectionPath().contains("Machine:M-05")));

        // FIXED: M-06 via PRD-106 must NOT be discovered as PRIMARY for BATCH-1010
        // Previous buggy behavior created PRODUCT_MACHINE_MATCH for M-06, which incorrectly promoted
        // M-07/M-06 (used by BATCH-1028 sharing PRD-106) to BATCH-1010.
        // Correct behavior: product->machine inference without direct batch reference is CROSS_BATCH_CONTEXT, not PRIMARY.
        // Therefore M-06 must NOT have any provenance for BATCH-1010 (neither MACHINE_MATCH nor PRODUCT_MACHINE_MATCH)
        boolean m06HasAny = explanations.stream().anyMatch(p -> "M-06".equals(p.getIntermediateEntityValue())
                || (p.getConnectionPath() != null && p.getConnectionPath().contains("Machine:M-06")));
        org.junit.jupiter.api.Assertions.assertFalse(m06HasAny, "M-06 should NOT be discovered for BATCH-1010 - it belongs to BATCH-1028 via PRD-106 and is CROSS_BATCH_CONTEXT");

        // M-06 must NEVER have a direct MACHINE_MATCH_FROM_BATCH without Product
        boolean m06HasDirect = explanations.stream().anyMatch(p -> "MACHINE_MATCH_FROM_BATCH".equals(p.getReason())
                && "M-06".equals(p.getIntermediateEntityValue()));
        org.junit.jupiter.api.Assertions.assertFalse(m06HasDirect, "M-06 should be indirect via Product, never direct from Batch");
    }

    private IngestedSourceRecord record(String sourceType, String sourceRecordId, String payload,
                                        String batch, String machine, String supplier, String product) {
        return IngestedSourceRecord.builder()
                .id((long) Math.abs(sourceRecordId.hashCode()))
                .organisation(organisation)
                .sourceType(sourceType)
                .sourceRecordId(sourceRecordId)
                .payload(payload)
                .batchReference(batch)
                .machineReference(machine)
                .supplierReference(supplier)
                .productReference(product)
                .build();
    }
}