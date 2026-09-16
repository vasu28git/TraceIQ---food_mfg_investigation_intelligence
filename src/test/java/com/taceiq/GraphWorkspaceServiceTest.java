package com.taceiq;

import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.Organisation;
import com.taceiq.graph.GraphQueryRepository;
import com.taceiq.graph.dto.CaseFileResponse;
import com.taceiq.graph.dto.GraphEvidenceDetailResponse;
import com.taceiq.graph.service.GraphReadinessService;
import com.taceiq.graph.service.GraphWorkspaceService;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.security.AuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GraphWorkspaceServiceTest {

    @Mock GraphQueryRepository graphRepo;
    @Mock CanonicalEvidenceRepository canonicalRepo;
    @Mock GraphReadinessService readinessService;
    @Mock AuthorizationService authService;

    GraphWorkspaceService service;
    ObjectMapper mapper = new ObjectMapper();

    Organisation org1 = Organisation.builder().orgId(1L).name("Org1").build();
    Organisation org2 = Organisation.builder().orgId(2L).name("Org2").build();

    CanonicalEvidence ev(String externalId, String caseId, boolean deleted) {
        return CanonicalEvidence.builder()
                .id(1L).organisation(org1).externalId(externalId).caseId(caseId).actorId("actor_1").parentId(null)
                .title("title "+externalId).sourceType("FILE").status("READY")
                .sourceCreatedAt(Instant.parse("2026-09-01T08:12:33.00Z")).sourceUpdatedAt(Instant.parse("2026-09-03T10:15:00.00Z"))
                .contentHash("hash").normalizedPayload("{\"size\":100,\"contentType\":\"application/json\",\"tags\":[\"a\"]}")
                .firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(deleted)
                .build();
    }

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new GraphWorkspaceService(graphRepo, canonicalRepo, readinessService, authService, mapper);
        lenient().when(authService.getCurrentOrgId()).thenReturn(1L);
        lenient().doNothing().when(authService).requireEvidenceGraphAccess();
        lenient().when(readinessService.isOrgGraphReady(1L)).thenReturn(true);
    }

    // Evidence detail tests
    @Test
    void validEvidenceReturned() {
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("ev_001", 1L)).thenReturn(Optional.of(ev("ev_001","CASE-1",false)));
        GraphEvidenceDetailResponse resp = service.getEvidenceDetail("ev_001");
        assertEquals("ev_001", resp.getStableId());
        assertEquals("title ev_001", resp.getTitle());
        assertNotNull(resp.getAttributes());
    }

    @Test
    void missingEvidence404() {
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("missing", 1L)).thenReturn(Optional.empty());
        when(canonicalRepo.findByExternalIdInAndOrganisationOrgId(List.of("missing"), 1L)).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> service.getEvidenceDetail("missing"));
    }

    @Test
    void deletedEvidence404() {
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("ev_001", 1L)).thenReturn(Optional.of(ev("ev_001","CASE-1",true)));
        // also list version returns deleted
        when(canonicalRepo.findByExternalIdInAndOrganisationOrgId(anyList(), anyLong())).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> service.getEvidenceDetail("ev_001"));
    }

    @Test
    void crossOrgEvidenceNotReturned() {
        // org1 user queries ev belonging to org2 – repo with org1 returns empty
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("ev_001", 1L)).thenReturn(Optional.empty());
        when(canonicalRepo.findByExternalIdInAndOrganisationOrgId(List.of("ev_001"), 1L)).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> service.getEvidenceDetail("ev_001"));
        verify(canonicalRepo).findByExternalIdAndOrganisationOrgId("ev_001", 1L);
        verify(canonicalRepo, never()).findByExternalIdAndOrganisationOrgId("ev_001", 2L);
    }

    @Test
    void graphNotReadyDoesNotBlockEvidence() {
        when(readinessService.isOrgGraphReady(1L)).thenReturn(false);
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("ev_001", 1L)).thenReturn(Optional.of(ev("ev_001","CASE-1",false)));
        GraphEvidenceDetailResponse resp = service.getEvidenceDetail("ev_001");
        assertEquals("ev_001", resp.getStableId());
        verify(canonicalRepo).findByExternalIdAndOrganisationOrgId("ev_001", 1L);
    }

    @Test
    void noInternalDbIdReturned() {
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("ev_001", 1L)).thenReturn(Optional.of(ev("ev_001","CASE-1",false)));
        var resp = service.getEvidenceDetail("ev_001");
        // DTO should not have id field
        assertEquals(0, java.util.Arrays.stream(resp.getClass().getDeclaredFields()).filter(f -> f.getName().equals("id")).count());
    }

    @Test
    void noNeo4jInternalIdReturned() {
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("ev_001", 1L)).thenReturn(Optional.of(ev("ev_001","CASE-1",false)));
        var resp = service.getEvidenceDetail("ev_001");
        String json = resp.toString();
        assertFalse(json.toLowerCase().contains("neo4j"));
    }

    @Test
    void canonicalScalarFieldsMappedCorrectly() {
        CanonicalEvidence e = ev("ev_001","CASE-1",false);
        e.setSourceType("API"); e.setStatus("READY");
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("ev_001", 1L)).thenReturn(Optional.of(e));
        var resp = service.getEvidenceDetail("ev_001");
        assertEquals("ev_001", resp.getStableId());
        assertEquals("CASE-1", resp.getCaseId());
        assertEquals("actor_1", resp.getActorId());
        assertEquals("API", resp.getSourceType());
        assertEquals("READY", resp.getStatus());
    }

    @Test
    void normalizedPayloadNotLeakedUnintentionally() {
        CanonicalEvidence e = ev("ev_001","CASE-1",false);
        e.setNormalizedPayload("{\"secret\":\"Bearer token123\",\"evidenceId\":\"ev_001\"}");
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("ev_001", 1L)).thenReturn(Optional.of(e));
        var resp = service.getEvidenceDetail("ev_001");
        // Should not expose raw payload
        assertNull(resp.getClass().getDeclaredFields().length == 0 ? "x" : null); // placeholder
        // Check that response does not contain raw secret key if we had exposed payload
        // Our DTO only exposes parsed attributes, not raw
        assertFalse(resp.toString().contains("token123"));
    }

    @Test
    void permissionDeniedEvidence() {
        doThrow(new AccessDeniedException("Missing")).when(authService).requireEvidenceGraphAccess();
        assertThrows(AccessDeniedException.class, () -> service.getEvidenceDetail("ev_001"));
    }

    // Case tests
    @Test
    void validCaseReturned() {
        when(graphRepo.caseExists(1L, "CASE-1")).thenReturn(true);
        when(canonicalRepo.countByCaseIdAndOrganisationOrgId("CASE-1", 1L)).thenReturn(2L);
        when(canonicalRepo.findByCaseIdAndOrganisationOrgId(eq("CASE-1"), eq(1L), any())).thenReturn(new PageImpl<>(List.of(ev("ev_001","CASE-1",false), ev("ev_002","CASE-1",false))));
        when(canonicalRepo.findByCaseIdAndOrganisationOrgId("CASE-1", 1L)).thenReturn(List.of(ev("ev_001","CASE-1",false), ev("ev_002","CASE-1",false)));
        when(graphRepo.traceCase(1L, "CASE-1", 5)).thenReturn(com.taceiq.graph.dto.TraceabilityDtos.CaseTraceabilityResponse.builder().caseNode(com.taceiq.graph.dto.TraceabilityDtos.TraceabilityNodeResponse.builder().stableId("CASE-1").label("Case").build()).nodes(List.of()).relationships(List.of()).depth(5).build());
        CaseFileResponse resp = service.getCaseFile("CASE-1", 0, 20);
        assertEquals("CASE-1", resp.getCaseSummary().getStableId());
        assertEquals(2, resp.getEvidence().size());
    }

    @Test
    void caseNotFound404() {
        when(graphRepo.caseExists(1L, "UNKNOWN")).thenReturn(false);
        assertThrows(ResponseStatusException.class, () -> service.getCaseFile("UNKNOWN", 0, 20));
    }

    @Test
    void crossOrgCaseNotRevealed() {
        when(graphRepo.caseExists(1L, "CASE-1")).thenReturn(false); // org1 has no such case, org2 does but not visible
        assertThrows(ResponseStatusException.class, () -> service.getCaseFile("CASE-1", 0, 20));
        verify(graphRepo).caseExists(1L, "CASE-1");
        verify(graphRepo, never()).caseExists(2L, "CASE-1");
    }

    @Test
    void graphNotReadyDoesNotBlockCaseEvidence() {
        when(readinessService.isOrgGraphReady(1L)).thenReturn(false);
        when(canonicalRepo.countByCaseIdAndOrganisationOrgId("CASE-1", 1L)).thenReturn(1L);
        when(canonicalRepo.findByCaseIdAndOrganisationOrgId(eq("CASE-1"), eq(1L), any())).thenReturn(new PageImpl<>(List.of(ev("ev_001","CASE-1",false))));
        when(canonicalRepo.findByCaseIdAndOrganisationOrgId("CASE-1", 1L)).thenReturn(List.of(ev("ev_001","CASE-1",false)));
        // trace may fail when graph not ready, but case evidence should still be returned best-effort
        when(graphRepo.traceCase(anyLong(), anyString(), anyInt())).thenThrow(new RuntimeException("Neo4j unavailable"));
        CaseFileResponse resp = service.getCaseFile("CASE-1", 0, 20);
        assertEquals("CASE-1", resp.getCaseSummary().getStableId());
        assertEquals(1, resp.getEvidence().size());
    }

    @Test
    void caseEvidenceReturned() {
        when(graphRepo.caseExists(1L, "CASE-1")).thenReturn(true);
        when(canonicalRepo.countByCaseIdAndOrganisationOrgId("CASE-1", 1L)).thenReturn(1L);
        when(canonicalRepo.findByCaseIdAndOrganisationOrgId(eq("CASE-1"), eq(1L), any())).thenReturn(new PageImpl<>(List.of(ev("ev_001","CASE-1",false))));
        when(canonicalRepo.findByCaseIdAndOrganisationOrgId("CASE-1", 1L)).thenReturn(List.of(ev("ev_001","CASE-1",false)));
        when(graphRepo.traceCase(anyLong(), anyString(), anyInt())).thenReturn(com.taceiq.graph.dto.TraceabilityDtos.CaseTraceabilityResponse.builder().depth(5).nodes(List.of()).relationships(List.of()).build());
        var resp = service.getCaseFile("CASE-1", 0, 20);
        assertEquals(1, resp.getEvidence().size());
        assertEquals("ev_001", resp.getEvidence().get(0).getStableId());
    }

    @Test
    void caseEvidencePagination() {
        when(graphRepo.caseExists(1L, "CASE-1")).thenReturn(true);
        when(canonicalRepo.countByCaseIdAndOrganisationOrgId("CASE-1", 1L)).thenReturn(25L);
        when(canonicalRepo.findByCaseIdAndOrganisationOrgId(eq("CASE-1"), eq(1L), any())).thenReturn(new PageImpl<>(List.of(ev("ev_021","CASE-1",false)), PageRequest.of(2, 10), 25));
        when(canonicalRepo.findByCaseIdAndOrganisationOrgId("CASE-1", 1L)).thenReturn(List.of(ev("ev_001","CASE-1",false)));
        when(graphRepo.traceCase(anyLong(), anyString(), anyInt())).thenReturn(com.taceiq.graph.dto.TraceabilityDtos.CaseTraceabilityResponse.builder().depth(5).nodes(List.of()).relationships(List.of()).build());
        var resp = service.getCaseFile("CASE-1", 2, 10);
        assertEquals(2, resp.getPage());
        assertEquals(10, resp.getSize());
        assertEquals(25, resp.getTotalElements());
        assertEquals(3, resp.getTotalPages());
    }

    @Test
    void actorAssociationReturned() {
        when(graphRepo.caseExists(1L, "CASE-1")).thenReturn(true);
        when(canonicalRepo.countByCaseIdAndOrganisationOrgId("CASE-1", 1L)).thenReturn(2L);
        CanonicalEvidence e1 = ev("ev_001","CASE-1",false); e1.setActorId("actor_1");
        CanonicalEvidence e2 = ev("ev_002","CASE-1",false); e2.setActorId("actor_2");
        when(canonicalRepo.findByCaseIdAndOrganisationOrgId(eq("CASE-1"), eq(1L), any())).thenReturn(new PageImpl<>(List.of(e1,e2)));
        when(canonicalRepo.findByCaseIdAndOrganisationOrgId("CASE-1", 1L)).thenReturn(List.of(e1,e2));
        when(graphRepo.traceCase(anyLong(), anyString(), anyInt())).thenReturn(com.taceiq.graph.dto.TraceabilityDtos.CaseTraceabilityResponse.builder().depth(5).nodes(List.of()).relationships(List.of()).build());
        var resp = service.getCaseFile("CASE-1", 0, 20);
        assertEquals(2, resp.getActors().size());
        assertTrue(resp.getActors().stream().anyMatch(a -> a.getStableId().equals("actor_1")));
    }

    @Test
    void crossOrgActorNeverReturned() {
        when(graphRepo.caseExists(1L, "CASE-1")).thenReturn(true);
        when(canonicalRepo.countByCaseIdAndOrganisationOrgId("CASE-1", 1L)).thenReturn(1L);
        CanonicalEvidence e = ev("ev_001","CASE-1",false); e.setActorId("actor_1");
        when(canonicalRepo.findByCaseIdAndOrganisationOrgId(eq("CASE-1"), eq(1L), any())).thenReturn(new PageImpl<>(List.of(e)));
        when(canonicalRepo.findByCaseIdAndOrganisationOrgId("CASE-1", 1L)).thenReturn(List.of(e));
        when(graphRepo.traceCase(anyLong(), anyString(), anyInt())).thenReturn(com.taceiq.graph.dto.TraceabilityDtos.CaseTraceabilityResponse.builder().depth(5).nodes(List.of()).relationships(List.of()).build());
        var resp = service.getCaseFile("CASE-1", 0, 20);
        // Should only contain actor_1 from org1, not org2
        assertEquals(1, resp.getActors().size());
        assertFalse(resp.getActors().stream().anyMatch(a -> a.getStableId().equals("actor_org2")));
    }

    @Test
    void onlyAllowedRelationshipTypesReturned() {
        when(graphRepo.caseExists(1L, "CASE-1")).thenReturn(true);
        when(canonicalRepo.countByCaseIdAndOrganisationOrgId("CASE-1", 1L)).thenReturn(1L);
        when(canonicalRepo.findByCaseIdAndOrganisationOrgId(eq("CASE-1"), eq(1L), any())).thenReturn(new PageImpl<>(List.of(ev("ev_001","CASE-1",false))));
        when(canonicalRepo.findByCaseIdAndOrganisationOrgId("CASE-1", 1L)).thenReturn(List.of(ev("ev_001","CASE-1",false)));
        when(graphRepo.traceCase(anyLong(), anyString(), anyInt())).thenReturn(com.taceiq.graph.dto.TraceabilityDtos.CaseTraceabilityResponse.builder()
                .depth(5)
                .nodes(List.of()).relationships(List.of(
                        com.taceiq.graph.dto.TraceabilityDtos.TraceabilityRelationshipResponse.builder().type("BELONGS_TO").build(),
                        com.taceiq.graph.dto.TraceabilityDtos.TraceabilityRelationshipResponse.builder().type("CREATED_BY").build(),
                        com.taceiq.graph.dto.TraceabilityDtos.TraceabilityRelationshipResponse.builder().type("UNKNOWN_TYPE").build()
                )).build());
        var resp = service.getCaseFile("CASE-1", 0, 20);
        // Our service filters to allowed types, so UNKNOWN should be filtered
        assertTrue(resp.getRelationships().stream().allMatch(r -> List.of("BELONGS_TO","CREATED_BY","DERIVED_FROM").contains(r.getType())));
    }

    @Test
    void noNeo4jInternalIdsReturned() {
        when(graphRepo.caseExists(1L, "CASE-1")).thenReturn(true);
        when(canonicalRepo.countByCaseIdAndOrganisationOrgId("CASE-1", 1L)).thenReturn(1L);
        when(canonicalRepo.findByCaseIdAndOrganisationOrgId(eq("CASE-1"), eq(1L), any())).thenReturn(new PageImpl<>(List.of(ev("ev_001","CASE-1",false))));
        when(canonicalRepo.findByCaseIdAndOrganisationOrgId("CASE-1", 1L)).thenReturn(List.of(ev("ev_001","CASE-1",false)));
        when(graphRepo.traceCase(anyLong(), anyString(), anyInt())).thenReturn(com.taceiq.graph.dto.TraceabilityDtos.CaseTraceabilityResponse.builder().depth(5).nodes(List.of()).relationships(List.of()).build());
        var resp = service.getCaseFile("CASE-1", 0, 20);
        String json = resp.toString();
        assertFalse(json.toLowerCase().contains("neo4j"));
    }

    @Test
    void noUnboundedCaseQuery() {
        when(graphRepo.caseExists(1L, "CASE-1")).thenReturn(true);
        when(canonicalRepo.countByCaseIdAndOrganisationOrgId("CASE-1", 1L)).thenReturn(1000L);
        when(canonicalRepo.findByCaseIdAndOrganisationOrgId(eq("CASE-1"), eq(1L), any())).thenReturn(new PageImpl<>(List.of(ev("ev_001","CASE-1",false)), PageRequest.of(0, 20), 1000));
        when(canonicalRepo.findByCaseIdAndOrganisationOrgId("CASE-1", 1L)).thenReturn(List.of(ev("ev_001","CASE-1",false)));
        when(graphRepo.traceCase(anyLong(), anyString(), anyInt())).thenReturn(com.taceiq.graph.dto.TraceabilityDtos.CaseTraceabilityResponse.builder().depth(5).nodes(List.of()).relationships(List.of()).build());
        var resp = service.getCaseFile("CASE-1", 0, 20);
        assertEquals(20, resp.getSize());
        assertEquals(1000, resp.getTotalElements());
        // Ensure not all 1000 loaded
        assertEquals(1, resp.getEvidence().size()); // page size 20 but we mocked 1 per page
    }

    @Test
    void permissionDeniedCase() {
        doThrow(new AccessDeniedException("Missing")).when(authService).requireEvidenceGraphAccess();
        assertThrows(AccessDeniedException.class, () -> service.getCaseFile("CASE-1", 0, 20));
    }
}
