package com.taceiq;

import com.taceiq.dto.CreateInvestigationRequest;
import com.taceiq.dto.InvestigationResponse;
import com.taceiq.entity.Investigation;
import com.taceiq.entity.Organisation;
import com.taceiq.entity.User;
import com.taceiq.graph.service.GraphReadinessService;
import com.taceiq.repository.InvestigationRepository;
import com.taceiq.repository.OrganisationRepository;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.InvestigationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Phase 2: Incident creation independent of Neo4j + incident context fields
public class IncidentCreationPhase2Test {

    @Mock InvestigationRepository investigationRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock AuthorizationService authorizationService;
    @Mock GraphReadinessService graphReadinessService;

    InvestigationService service;

    Organisation org1 = Organisation.builder().orgId(1L).name("Org1").build();
    Organisation org2 = Organisation.builder().orgId(2L).name("Org2").build();
    User user1 = User.builder().id(10L).username("user1").organisation(org1).build();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new InvestigationService(investigationRepository, organisationRepository, authorizationService, graphReadinessService);
        lenient().when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        lenient().when(authorizationService.getCurrentUser()).thenReturn(user1);
        lenient().doNothing().when(authorizationService).requireEvidenceGraphAccess();
        lenient().when(organisationRepository.getReferenceById(1L)).thenReturn(org1);
        lenient().when(organisationRepository.getReferenceById(2L)).thenReturn(org2);
        lenient().when(investigationRepository.save(any())).thenAnswer(i -> {
            Investigation inv = i.getArgument(0);
            inv.setId(100L);
            // simulate @PrePersist already set, but ensure fields preserved
            if (inv.getCreatedAt() == null) inv.setCreatedAt(Instant.now());
            if (inv.getUpdatedAt() == null) inv.setUpdatedAt(Instant.now());
            return inv;
        });
        lenient().when(investigationRepository.existsByInvestigationKeyAndOrganisationOrgId(anyString(), anyLong())).thenReturn(false);
    }

    // A. Create incident with only required fields — should succeed without Neo4j readiness
    @Test
    void createWithOnlyRequiredFields_succeeds() {
        // explicitly mock graph not ready — should still succeed in Phase 2
        when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(false);
        CreateInvestigationRequest req = CreateInvestigationRequest.builder()
                .investigationKey("INC-001").title("Batch issue").build();
        InvestigationResponse resp = service.create(req);
        assertEquals("INC-001", resp.getInvestigationKey());
        assertEquals("DRAFT", resp.getStatus());
        verify(investigationRepository).save(any());
        // graph readiness must NOT be checked for creation (verify not called or not throwing)
        // In Phase 2 we removed the ensureGraphReady call, so graphReadinessService.isOrgGraphReady should not be invoked on create
        // Lenient mock allows either, but we verify no BAD_REQUEST was thrown
    }

    @Test
    void createWithOnlyRequiredFields_doesNotCallGraphReadiness() {
        CreateInvestigationRequest req = CreateInvestigationRequest.builder()
                .investigationKey("INC-002").title("Title").build();
        service.create(req);
        verify(graphReadinessService, never()).isOrgGraphReady(anyLong());
        verify(graphReadinessService, never()).getCanonicalEvidenceCountsForDiagnostics(anyLong());
    }

    // B. Create incident with full incident context
    @Test
    void createWithFullIncidentContext_persistsAllFields() {
        Instant start = Instant.parse("2026-09-01T08:00:00Z");
        Instant end = Instant.parse("2026-09-03T10:00:00Z");
        CreateInvestigationRequest req = CreateInvestigationRequest.builder()
                .investigationKey("INC-100").title("Full context")
                .description("desc")
                .batchReference("BATCH-001")
                .productReference("PROD-X")
                .orderReference("ORD-99")
                .incidentStart(start)
                .incidentEnd(end)
                .build();
        InvestigationResponse resp = service.create(req);
        assertEquals("BATCH-001", resp.getBatchReference());
        assertEquals("PROD-X", resp.getProductReference());
        assertEquals("ORD-99", resp.getOrderReference());
        assertEquals(start, resp.getIncidentStart());
        assertEquals(end, resp.getIncidentEnd());

        ArgumentCaptor<Investigation> cap = ArgumentCaptor.forClass(Investigation.class);
        verify(investigationRepository).save(cap.capture());
        Investigation saved = cap.getValue();
        assertEquals("BATCH-001", saved.getBatchReference());
        assertEquals("PROD-X", saved.getProductReference());
        assertEquals("ORD-99", saved.getOrderReference());
        assertEquals(start, saved.getIncidentStart());
        assertEquals(end, saved.getIncidentEnd());
    }

    // C. Create incident with partial context (only batchReference)
    @Test
    void createWithPartialContext_onlyBatchReference() {
        CreateInvestigationRequest req = CreateInvestigationRequest.builder()
                .investigationKey("INC-101").title("Partial")
                .batchReference("BATCH-ONLY")
                .build();
        InvestigationResponse resp = service.create(req);
        assertEquals("BATCH-ONLY", resp.getBatchReference());
        assertNull(resp.getProductReference());
        assertNull(resp.getOrderReference());
        assertNull(resp.getIncidentStart());
        assertNull(resp.getIncidentEnd());
    }

    // D. Invalid date range — incidentEnd earlier than incidentStart
    @Test
    void invalidDateRange_rejected() {
        Instant start = Instant.parse("2026-09-03T10:00:00Z");
        Instant end = Instant.parse("2026-09-01T08:00:00Z"); // before start
        CreateInvestigationRequest req = CreateInvestigationRequest.builder()
                .investigationKey("INC-102").title("Invalid dates")
                .incidentStart(start).incidentEnd(end)
                .build();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.create(req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("incidentEnd must not be before incidentStart"));
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void validDateRange_equalTimesAllowed() {
        Instant t = Instant.parse("2026-09-01T08:00:00Z");
        CreateInvestigationRequest req = CreateInvestigationRequest.builder()
                .investigationKey("INC-103").title("Equal").incidentStart(t).incidentEnd(t).build();
        assertDoesNotThrow(() -> service.create(req));
    }

    @Test
    void onlyOneDateSupplied_allowed() {
        Instant start = Instant.parse("2026-09-01T08:00:00Z");
        CreateInvestigationRequest req1 = CreateInvestigationRequest.builder()
                .investigationKey("INC-104").title("Only start").incidentStart(start).build();
        assertDoesNotThrow(() -> service.create(req1));

        CreateInvestigationRequest req2 = CreateInvestigationRequest.builder()
                .investigationKey("INC-105").title("Only end").incidentEnd(start).build();
        assertDoesNotThrow(() -> service.create(req2));
    }

    // E. Tenant isolation — created incident belongs to authenticated org
    @Test
    void tenantIsolation_belongsToCurrentOrganisation() {
        when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        CreateInvestigationRequest req = CreateInvestigationRequest.builder()
                .investigationKey("INC-TENANT").title("Tenant test").build();
        service.create(req);
        verify(organisationRepository).getReferenceById(1L);
        verify(organisationRepository, never()).getReferenceById(2L);
        verify(investigationRepository).existsByInvestigationKeyAndOrganisationOrgId("INC-TENANT", 1L);
    }

    // F. Neo4j unavailable — still succeeds (Phase 2 core requirement)
    @Test
    void neo4jUnavailable_stillSucceeds() {
        when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(false);
        // Also simulate diagnostic throwing
        when(graphReadinessService.getCanonicalEvidenceCountsForDiagnostics(1L)).thenThrow(new RuntimeException("Neo4j down"));
        // Phase 2 must not call graph readiness at all, so these mocks should not cause failure
        // Reset to verify never called
        CreateInvestigationRequest req = CreateInvestigationRequest.builder()
                .investigationKey("INC-NEO4J").title("Neo4j down test").build();
        InvestigationResponse resp = service.create(req);
        assertEquals("DRAFT", resp.getStatus());
        verify(investigationRepository).save(any());
    }

    @Test
    void neo4jUnavailable_withFullContext_stillSucceeds() {
        when(graphReadinessService.isOrgGraphReady(anyLong())).thenReturn(false);
        Instant start = Instant.parse("2026-09-01T08:00:00Z");
        Instant end = Instant.parse("2026-09-02T08:00:00Z");
        CreateInvestigationRequest req = CreateInvestigationRequest.builder()
                .investigationKey("INC-NEO4J2").title("Full with Neo4j down")
                .batchReference("B1").productReference("P1").orderReference("O1")
                .incidentStart(start).incidentEnd(end).build();
        InvestigationResponse resp = service.create(req);
        assertEquals("B1", resp.getBatchReference());
        verify(investigationRepository).save(any());
    }

    // G. Existing request compatibility — old payload without new fields
    @Test
    void existingRequestCompatibility_oldPayloadStillWorks() {
        CreateInvestigationRequest req = new CreateInvestigationRequest();
        req.setInvestigationKey("INC-OLD");
        req.setTitle("Old title");
        req.setDescription("Old desc");
        // batch/product/order/start/end remain null (as old clients send)
        InvestigationResponse resp = service.create(req);
        assertEquals("INC-OLD", resp.getInvestigationKey());
        assertEquals("Old title", resp.getTitle());
        assertEquals("Old desc", resp.getDescription());
        assertNull(resp.getBatchReference());
        assertNull(resp.getProductReference());
        assertNull(resp.getOrderReference());
        assertNull(resp.getIncidentStart());
        assertNull(resp.getIncidentEnd());
        verify(investigationRepository).save(any());
    }

    // Additional edge: blank strings trimmed to null
    @Test
    void blankIncidentContextTrimmedToNull() {
        CreateInvestigationRequest req = CreateInvestigationRequest.builder()
                .investigationKey("INC-BLANK").title("Blank")
                .batchReference("   ").productReference("  ").orderReference("").build();
        InvestigationResponse resp = service.create(req);
        assertNull(resp.getBatchReference());
        assertNull(resp.getProductReference());
        assertNull(resp.getOrderReference());
    }

    @Test
    void batchReferenceMax100_rejected() {
        String longBatch = "a".repeat(101);
        CreateInvestigationRequest req = CreateInvestigationRequest.builder()
                .investigationKey("INC-LONG").title("Long").batchReference(longBatch).build();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.create(req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("batchReference max 100"));
    }
}
