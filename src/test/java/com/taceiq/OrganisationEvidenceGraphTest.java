package com.taceiq;

import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.IngestedSourceRecord;
import com.taceiq.entity.Organisation;
import com.taceiq.graph.GraphQueryRepository;
import com.taceiq.graph.dto.GraphEvidencePageResponse;
import com.taceiq.graph.dto.TraceabilityDtos.IncidentTraceabilityResponse;
import com.taceiq.graph.dto.TraceabilityDtos.TraceabilityNodeResponse;
import com.taceiq.graph.dto.TraceabilityDtos.TraceabilityRelationshipResponse;
import com.taceiq.graph.service.GraphQueryService;
import com.taceiq.graph.service.GraphReadinessService;
import com.taceiq.repository.*;
import com.taceiq.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class OrganisationEvidenceGraphTest {

    @Mock private GraphQueryRepository queryRepo;
    @Mock private GraphReadinessService readinessService;
    @Mock private AuthorizationService authService;
    @Mock private ConfigurationRepository configRepo;
    @Mock private ConfigurationDefinitionRepository defRepo;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private CanonicalEvidenceRepository canonicalEvidenceRepository;
    @Mock private IngestedSourceRecordRepository ingestedSourceRecordRepository;

    private GraphQueryService queryService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        queryService = new GraphQueryService(
                queryRepo,
                readinessService,
                authService,
                configRepo,
                defRepo,
                investigationRepository,
                canonicalEvidenceRepository,
                ingestedSourceRecordRepository
        );

        when(authService.getCurrentOrgId()).thenReturn(1L);
        doNothing().when(authService).requireEvidenceGraphAccess();
        doNothing().when(authService).requireTraceabilityAccess();
    }

    private List<IngestedSourceRecord> generate43SampleSourceRecords(Long orgId) {
        Organisation org = Organisation.builder().orgId(orgId).name("Test Org").build();
        List<IngestedSourceRecord> list = new ArrayList<>();

        Map<String, Integer> dist = new LinkedHashMap<>();
        dist.put("CMMS", 6);
        dist.put("ERP", 4);
        dist.put("LIMS", 6);
        dist.put("MES", 6);
        dist.put("PACKAGING", 4);
        dist.put("SHIPMENT", 4);
        dist.put("SOP", 6);
        dist.put("WAREHOUSE", 7);

        long id = 1;
        for (Map.Entry<String, Integer> entry : dist.entrySet()) {
            for (int i = 1; i <= entry.getValue(); i++) {
                list.add(IngestedSourceRecord.builder()
                        .id(id++)
                        .organisation(org)
                        .sourceType(entry.getKey())
                        .sourceRecordId("REC-" + entry.getKey() + "-" + i)
                        .batchReference(i == 1 ? "BATCH-1010" : "BATCH-OTHER")
                        .ingestedAt(Instant.now())
                        .build());
            }
        }
        return list;
    }

    @Test
    @DisplayName("A. Organisation 1 can retrieve its 43 available source records")
    void testA_organisation1CanRetrieve43SourceRecords() {
        List<IngestedSourceRecord> records = generate43SampleSourceRecords(1L);
        assertEquals(43, records.size());

        when(ingestedSourceRecordRepository.findByOrganisationOrgId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(records));

        GraphEvidencePageResponse response = queryService.searchEvidence(null, null, 0, 50);

        assertNotNull(response);
        assertEquals(43, response.getTotalElements());
        assertEquals(43, response.getContent().size());
        assertEquals("SRC_CMMS_REC-CMMS-1", response.getContent().get(0).getStableId());
        assertEquals("CMMS", response.getContent().get(0).getSourceType());
        verify(ingestedSourceRecordRepository).findByOrganisationOrgId(eq(1L), any(Pageable.class));
    }

    @Test
    @DisplayName("B. Tenant Isolation: No records from another organisation are returned")
    void testB_tenantIsolation_neverReturnsCrossTenantRecords() {
        List<IngestedSourceRecord> org1Records = generate43SampleSourceRecords(1L);
        when(ingestedSourceRecordRepository.findByOrganisationOrgId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(org1Records));
        when(ingestedSourceRecordRepository.findByOrganisationOrgId(eq(2L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        // Query as Org 1
        GraphEvidencePageResponse org1Resp = queryService.searchEvidence(null, null, 0, 50);
        assertEquals(43, org1Resp.getTotalElements());
        verify(ingestedSourceRecordRepository).findByOrganisationOrgId(eq(1L), any(Pageable.class));
        verify(ingestedSourceRecordRepository, never()).findByOrganisationOrgId(eq(2L), any(Pageable.class));

        // Switch to Org 2
        when(authService.getCurrentOrgId()).thenReturn(2L);
        GraphEvidencePageResponse org2Resp = queryService.searchEvidence(null, null, 0, 50);
        assertEquals(0, org2Resp.getTotalElements());
        verify(ingestedSourceRecordRepository).findByOrganisationOrgId(eq(2L), any(Pageable.class));
    }

    @Test
    @DisplayName("C. Organisation evidence loading does not require an investigation graph to exist")
    void testC_doesNotRequireInvestigationGraphToExist() {
        List<IngestedSourceRecord> records = generate43SampleSourceRecords(1L);
        when(ingestedSourceRecordRepository.findByOrganisationOrgId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(records));
        // No canonical evidence exists
        when(canonicalEvidenceRepository.findByOrganisationOrgIdAndIsDeletedFalse(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        GraphEvidencePageResponse resp = queryService.searchEvidence(null, null, null, 0, 20, null);

        assertNotNull(resp);
        assertEquals(43, resp.getTotalElements());
        assertEquals(43, resp.getContent().size());
        verify(readinessService, never()).isOrgGraphReady(anyLong());
    }

    @Test
    @DisplayName("D. Missing/unavailable Neo4j does not turn PostgreSQL evidence-list into HTTP 500")
    void testD_missingOrUnavailableNeo4j_doesNotCause500ForEvidenceList() {
        List<IngestedSourceRecord> records = generate43SampleSourceRecords(1L);
        when(ingestedSourceRecordRepository.findByOrganisationOrgId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(records));
        // Neo4j is reported not ready / throws exception
        when(readinessService.isOrgGraphReady(anyLong())).thenReturn(false);

        // searchEvidence should succeed strictly from PostgreSQL without throwing
        assertDoesNotThrow(() -> {
            GraphEvidencePageResponse resp = queryService.searchEvidence(null, null, 0, 20);
            assertNotNull(resp);
            assertEquals(43, resp.getTotalElements());
        });
    }

    @Test
    @DisplayName("E. Existing investigation traceability remains correct")
    void testE_investigationTraceabilityRemainsCorrect() {
        when(investigationRepository.findByIdAndOrganisationOrgId(eq(1L), eq(1L)))
                .thenReturn(Optional.of(com.taceiq.entity.Investigation.builder().id(1L).build()));
        when(readinessService.isOrgGraphReady(1L)).thenReturn(true);
        when(queryRepo.incidentExists(1L, 1L)).thenReturn(true);

        IncidentTraceabilityResponse mockTrace = IncidentTraceabilityResponse.builder()
                .incidentNode(TraceabilityNodeResponse.builder().stableId("1").label("Incident").build())
                .nodes(List.of(
                        TraceabilityNodeResponse.builder().stableId("ev_1").label("Evidence").build(),
                        TraceabilityNodeResponse.builder().stableId("batch_1").label("Batch").build()
                ))
                .relationships(List.of(
                        TraceabilityRelationshipResponse.builder().fromStableId("1").toStableId("batch_1").type("TARGETS").build()
                ))
                .depth(5)
                .build();

        when(queryRepo.traceIncident(eq(1L), eq(1L), anyInt())).thenReturn(mockTrace);

        IncidentTraceabilityResponse trace = queryService.traceIncident(1L);
        assertNotNull(trace);
        assertEquals("1", trace.getIncidentNode().getStableId());
        assertEquals(2, trace.getNodes().size());
        assertEquals(1, trace.getRelationships().size());
        verify(queryRepo).traceIncident(eq(1L), eq(1L), anyInt());
    }

    @Test
    @DisplayName("F. No duplicate records are introduced")
    void testF_noDuplicateRecordsIntroduced() {
        List<IngestedSourceRecord> records = generate43SampleSourceRecords(1L);
        when(ingestedSourceRecordRepository.findByOrganisationOrgId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(records));

        GraphEvidencePageResponse resp = queryService.searchEvidence(null, null, 0, 50);
        Set<String> seenStableIds = new HashSet<>();
        for (var item : resp.getContent()) {
            assertTrue(seenStableIds.add(item.getStableId()), "Duplicate stableId detected: " + item.getStableId());
        }
        assertEquals(43, seenStableIds.size());
    }

    @Test
    @DisplayName("G. Existing legacy API callers with caseId/actorId remain compatible")
    void testG_legacyApiCallersRemainCompatible() {
        CanonicalEvidence canEv = CanonicalEvidence.builder()
                .externalId("CAN-001")
                .title("Legacy Case Evidence")
                .sourceType("LIMS")
                .caseId("CASE-99")
                .actorId("ACT-1")
                .status("VERIFIED")
                .build();

        when(canonicalEvidenceRepository.findByOrgAndCaseAndActor(eq(1L), eq("CASE-99"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(canEv)));
        when(canonicalEvidenceRepository.countByOrgAndCaseAndActor(eq(1L), eq("CASE-99"), isNull()))
                .thenReturn(1L);

        GraphEvidencePageResponse resp = queryService.searchEvidence("CASE-99", null, 0, 20);
        assertNotNull(resp);
        assertEquals(1, resp.getTotalElements());
        assertEquals("CAN-001", resp.getContent().get(0).getStableId());
        assertEquals("Legacy Case Evidence", resp.getContent().get(0).getTitle());
        verify(canonicalEvidenceRepository).findByOrgAndCaseAndActor(eq(1L), eq("CASE-99"), isNull(), any(Pageable.class));
    }
}
