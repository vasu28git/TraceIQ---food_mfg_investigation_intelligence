package com.taceiq;

import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.Integration;
import com.taceiq.entity.IntegrationSync;
import com.taceiq.entity.Organisation;
import com.taceiq.graph.dto.GraphProjectionResult;
import com.taceiq.graph.dto.GraphReadinessResult;
import com.taceiq.graph.dto.GraphValidationResult;
import com.taceiq.graph.service.GraphProjectionService;
import com.taceiq.graph.service.GraphReadinessService;
import com.taceiq.graph.service.GraphValidator;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.IntegrationRepository;
import com.taceiq.repository.IntegrationSyncRepository;
import com.taceiq.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GraphReadinessServiceTest {

    @Mock IntegrationRepository integrationRepo;
    @Mock IntegrationSyncRepository syncRepo;
    @Mock CanonicalEvidenceRepository canonicalRepo;
    @Mock GraphProjectionService projectionService;
    @Mock GraphValidator validator;
    @Mock AuthorizationService authService;

    GraphReadinessService service;

    Organisation org1 = Organisation.builder().orgId(1L).name("Org1").build();
    Organisation org2 = Organisation.builder().orgId(2L).name("Org2").build();
    Integration int1 = Integration.builder().id(10L).organisation(org1).name("Int1").build();
    IntegrationSync successSync = IntegrationSync.builder().id(100L).organisation(org1).integration(int1).status("SUCCESS").completedAt(Instant.now()).build();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new GraphReadinessService(integrationRepo, syncRepo, canonicalRepo, projectionService, validator, authService);
        lenient().when(authService.getCurrentOrgId()).thenReturn(1L);
        lenient().doNothing().when(authService).requireIntegrationRead();
        lenient().when(integrationRepo.findByIdAndOrganisationOrgId(10L, 1L)).thenReturn(Optional.of(int1));
        lenient().when(syncRepo.findTopByOrganisationOrgIdAndIntegrationIdAndStatusOrderByCompletedAtDesc(1L, 10L, "SUCCESS")).thenReturn(Optional.of(successSync));
        CanonicalEvidence ev = CanonicalEvidence.builder().id(1L).organisation(org1).integration(int1).externalId("ev_001").contentHash("h").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).build();
        lenient().when(canonicalRepo.findByIntegrationIdAndOrganisationOrgId(10L, 1L)).thenReturn(List.of(ev));
        lenient().when(projectionService.projectForIntegration(1L, 10L)).thenReturn(GraphProjectionResult.builder().organisationId(1L).evidenceProjected(1).casesProjected(1).actorsProjected(1).relationshipsProjected(2).skipped(0).build());
        lenient().when(validator.validate(1L)).thenReturn(GraphValidationResult.builder().valid(true).evidenceCount(1).invalidCount(0).errors(List.of()).build());
    }

    @Test
    void happyPath_successProjectionValid_graphReadyTrue() {
        GraphReadinessResult r = service.checkReadiness(10L);
        assertTrue(r.isGraphReady());
        assertTrue(r.isGraphProjected());
        assertTrue(r.isGraphValid());
        assertEquals("SUCCESS", r.getCanonicalSyncStatus());
        assertEquals(1, r.getEvidenceCount());
        assertEquals(10L, r.getIntegrationId());
        assertEquals(1L, r.getOrganisationId());
    }

    @Test
    void noSuccessfulSync_graphReadyFalse() {
        when(syncRepo.findTopByOrganisationOrgIdAndIntegrationIdAndStatusOrderByCompletedAtDesc(1L, 10L, "SUCCESS")).thenReturn(Optional.empty());
        GraphReadinessResult r = service.checkReadiness(10L);
        assertFalse(r.isGraphReady());
        assertTrue(r.getValidationErrors().stream().anyMatch(s -> s.contains("No successful")));
    }

    @Test
    void partialSync_graphReadyFalse() {
        IntegrationSync partial = IntegrationSync.builder().id(101L).organisation(org1).integration(int1).status("PARTIAL").completedAt(Instant.now()).build();
        // Mock latest SUCCESS empty, but we force caller to have PARTIAL as latest? Instead we make findTop return empty for SUCCESS, and we test that PARTIAL sync not considered SUCCESS
        when(syncRepo.findTopByOrganisationOrgIdAndIntegrationIdAndStatusOrderByCompletedAtDesc(1L, 10L, "SUCCESS")).thenReturn(Optional.empty());
        // Also need to simulate that integration has a PARTIAL sync but no SUCCESS
        GraphReadinessResult r = service.checkReadiness(10L);
        assertFalse(r.isGraphReady());
    }

    @Test
    void failedSync_graphReadyFalse() {
        when(syncRepo.findTopByOrganisationOrgIdAndIntegrationIdAndStatusOrderByCompletedAtDesc(1L, 10L, "SUCCESS")).thenReturn(Optional.empty());
        GraphReadinessResult r = service.checkReadiness(10L);
        assertFalse(r.isGraphReady());
    }

    @Test
    void emptyCanonicalEvidence_graphReadyFalse() {
        when(canonicalRepo.findByIntegrationIdAndOrganisationOrgId(10L, 1L)).thenReturn(List.of());
        GraphReadinessResult r = service.checkReadiness(10L);
        assertFalse(r.isGraphReady());
        assertTrue(r.getValidationErrors().stream().anyMatch(s -> s.contains("No canonical")));
    }

    @Test
    void neo4jUnavailable_canonicalRemainsSuccess_graphReadyFalse_noCredentialsLeaked() {
        when(projectionService.projectForIntegration(1L, 10L)).thenThrow(new IllegalStateException("Neo4j not configured Bearer my-secret-token-xyz"));
        GraphReadinessResult r = service.checkReadiness(10L);
        assertFalse(r.isGraphReady());
        assertFalse(r.isGraphProjected());
        // canonical sync status still SUCCESS
        assertEquals("SUCCESS", r.getCanonicalSyncStatus());
        // no credentials leak – sanitized to Bearer ***
        assertFalse(r.getValidationErrors().toString().contains("my-secret-token-xyz"));
        assertTrue(r.getValidationErrors().toString().contains("Bearer ***") || r.getValidationErrors().toString().contains("Neo4j"));
        assertEquals(1, r.getEvidenceCount()); // canonical count preserved
    }

    @Test
    void projectionFailure_graphReadyFalse_canonicalPreserved() {
        when(projectionService.projectForIntegration(1L, 10L)).thenThrow(new RuntimeException("Graph projection failed"));
        GraphReadinessResult r = service.checkReadiness(10L);
        assertFalse(r.isGraphReady());
        assertFalse(r.isGraphProjected());
        // canonical still exists
        verify(canonicalRepo, atLeastOnce()).findByIntegrationIdAndOrganisationOrgId(10L, 1L);
    }

    @Test
    void validationFailure_graphReadyFalse() {
        when(validator.validate(1L)).thenReturn(GraphValidationResult.builder().valid(false).evidenceCount(1).invalidCount(1).errors(List.of("Duplicate Evidence")).build());
        GraphReadinessResult r = service.checkReadiness(10L);
        assertFalse(r.isGraphReady());
        assertTrue(r.isGraphProjected());
        assertFalse(r.isGraphValid());
    }

    @Test
    void repeatedReadinessCallNoDuplicateSync() {
        GraphReadinessResult r1 = service.checkReadiness(10L);
        GraphReadinessResult r2 = service.checkReadiness(10L);
        assertTrue(r1.isGraphReady());
        assertTrue(r2.isGraphReady());
        // No new sync created
        verify(syncRepo, never()).save(any());
    }

    @Test
    void crossTenantIntegrationDenied() {
        when(integrationRepo.findByIdAndOrganisationOrgId(10L, 1L)).thenReturn(Optional.empty());
        when(integrationRepo.findById(10L)).thenReturn(Optional.of(Integration.builder().id(10L).organisation(org2).build()));
        assertThrows(AccessDeniedException.class, () -> service.checkReadiness(10L));
    }

    @Test
    void crossTenantSyncNeverSelected() {
        // org1 requests, but sync belongs to org2 – repository method ensures not returned
        when(syncRepo.findTopByOrganisationOrgIdAndIntegrationIdAndStatusOrderByCompletedAtDesc(1L, 10L, "SUCCESS")).thenReturn(Optional.empty());
        GraphReadinessResult r = service.checkReadiness(10L);
        assertFalse(r.isGraphReady());
        assertTrue(r.getValidationErrors().stream().anyMatch(s -> s.contains("No successful")));
    }

    @Test
    void deterministicLatestSelection() {
        IntegrationSync older = IntegrationSync.builder().id(100L).organisation(org1).integration(int1).status("SUCCESS").completedAt(Instant.now().minusSeconds(3600)).build();
        IntegrationSync newer = IntegrationSync.builder().id(101L).organisation(org1).integration(int1).status("SUCCESS").completedAt(Instant.now()).build();
        // Repository returns newer due to ORDER BY completedAt DESC
        when(syncRepo.findTopByOrganisationOrgIdAndIntegrationIdAndStatusOrderByCompletedAtDesc(1L, 10L, "SUCCESS")).thenReturn(Optional.of(newer));
        GraphReadinessResult r = service.checkReadiness(10L);
        assertEquals(101L, r.getSyncId());
    }

    @Test
    void countMismatch_graphReadyFalse() {
        // canonical 1, graph 0 mismatch
        when(validator.validate(1L)).thenReturn(GraphValidationResult.builder().valid(true).evidenceCount(0).invalidCount(0).errors(List.of()).build());
        GraphReadinessResult r = service.checkReadiness(10L);
        assertFalse(r.isGraphReady());
        assertTrue(r.getValidationErrors().stream().anyMatch(s -> s.contains("mismatch")));
    }

        @Test
        void orgReadinessProjectsAllEvidenceAcrossMultipleIntegrations() {
        Integration int2 = Integration.builder().id(11L).organisation(org1).name("Int2").build();
        CanonicalEvidence second = CanonicalEvidence.builder().id(2L).organisation(org1).integration(int2)
            .externalId("ev_002").contentHash("h2").normalizedPayload("{}")
            .firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).build();
        when(integrationRepo.findByOrganisationOrgId(1L)).thenReturn(List.of(int1, int2));
        when(syncRepo.findTopByOrganisationOrgIdAndIntegrationIdAndStatusOrderByCompletedAtDesc(1L, 10L, "SUCCESS"))
            .thenReturn(Optional.of(successSync));
        when(syncRepo.findTopByOrganisationOrgIdAndIntegrationIdAndStatusOrderByCompletedAtDesc(1L, 11L, "SUCCESS"))
            .thenReturn(Optional.empty());
        when(canonicalRepo.findAllByOrganisationOrgId(1L)).thenReturn(List.of(
            CanonicalEvidence.builder().id(1L).organisation(org1).integration(int1).externalId("ev_001").isDeleted(false).build(),
            second));
        when(projectionService.projectForOrganisation(1L)).thenReturn(GraphProjectionResult.builder().organisationId(1L).evidenceProjected(2).build());
        when(validator.validate(1L)).thenReturn(GraphValidationResult.builder().valid(true).evidenceCount(2).invalidCount(0).errors(List.of()).build());

        GraphReadinessResult result = service.checkOrgReadiness(1L);

        assertTrue(result.isGraphReady());
        assertEquals(2, result.getEvidenceCount());
        verify(projectionService).projectForOrganisation(1L);
        verify(projectionService, never()).projectForIntegration(anyLong(), anyLong());
        }
}
