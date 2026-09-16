package com.taceiq;

import com.taceiq.dto.CreateInvestigationRequest;
import com.taceiq.dto.InvestigationResponse;
import com.taceiq.entity.Investigation;
import com.taceiq.entity.Organisation;
import com.taceiq.entity.User;
import com.taceiq.graph.service.GraphReadinessService;
import com.taceiq.ingestion.EvidenceDiscoveryService;
import com.taceiq.repository.InvestigationRepository;
import com.taceiq.repository.OrganisationRepository;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.InvestigationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class InvestigationServiceTest {

    @Mock InvestigationRepository investigationRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock AuthorizationService authorizationService;
    @Mock GraphReadinessService graphReadinessService;
    @Mock EvidenceDiscoveryService discoveryService;

    InvestigationService service;

    Organisation org1 = Organisation.builder().orgId(1L).name("Org1").build();
    Organisation org2 = Organisation.builder().orgId(2L).name("Org2").build();
    User user1 = User.builder().id(10L).username("user1").organisation(org1).build();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new InvestigationService(investigationRepository, organisationRepository, authorizationService, graphReadinessService);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "discoveryService", discoveryService);
        lenient().when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        lenient().when(authorizationService.getCurrentUser()).thenReturn(user1);
        lenient().doNothing().when(authorizationService).requireEvidenceGraphAccess();
        lenient().when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(true);
        lenient().when(organisationRepository.getReferenceById(1L)).thenReturn(org1);
        lenient().when(investigationRepository.save(any())).thenAnswer(i -> {
            Investigation inv = i.getArgument(0);
            inv.setId(100L);
            return inv;
        });
    }

    CreateInvestigationRequest req(String key, String title) {
        return CreateInvestigationRequest.builder().investigationKey(key).title(title).description("desc").build();
    }

    Investigation existing(String status) {
        return Investigation.builder().id(100L).organisation(org1).investigationKey("INV-1").title("T").description("d").status(status).createdBy(user1).build();
    }

    // Creation
    @Test
    void validCreationDefaultDraft() {
        when(investigationRepository.existsByInvestigationKeyAndOrganisationOrgId("INV-1", 1L)).thenReturn(false);
        InvestigationResponse resp = service.create(req("INV-1", "Title"));
        assertEquals("DRAFT", resp.getStatus());
        assertEquals("INV-1", resp.getInvestigationKey());
        assertEquals(10L, resp.getCreatedByUserId());
        verify(investigationRepository).save(any());
    }

    @Test
    void duplicateKey409() {
        when(investigationRepository.existsByInvestigationKeyAndOrganisationOrgId("INV-1", 1L)).thenReturn(true);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.create(req("INV-1", "Title")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void missingTitle400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.create(req("INV-1", "  ")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void missingInvestigationKey400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.create(req("  ", "Title")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void permissionDeniedCreation() {
        doThrow(new AccessDeniedException("Missing")).when(authorizationService).requireEvidenceGraphAccess();
        assertThrows(AccessDeniedException.class, () -> service.create(req("INV-1", "Title")));
    }

    @Test
    void existingInvestigationCanRecoverEvidenceWithinTenant() {
        Investigation inv = existing("DRAFT");
        inv.setBatchReference("BATCH-1010");
        when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(inv));
        EvidenceDiscoveryService.DiscoveryResult result = new EvidenceDiscoveryService.DiscoveryResult();
        result.incidentId = 100L;
        result.batchReference = "BATCH-1010";
        result.discovered = 17;
        result.reused = 17;
        when(discoveryService.discoverForIncident(100L, 1L)).thenReturn(result);

        EvidenceDiscoveryService.DiscoveryResult recovered = service.discoverEvidence(100L);

        assertSame(result, recovered);
        verify(authorizationService).requireEvidenceGraphAccess();
        verify(discoveryService).discoverForIncident(100L, 1L);
        verify(investigationRepository).findByIdAndOrganisationOrgId(100L, 1L);
    }

    @Test
    void graphNotReadyCreation400() {
        // Phase 2: incident creation MUST NOT require Neo4j — PostgreSQL is source of truth
        when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(false);
        when(investigationRepository.existsByInvestigationKeyAndOrganisationOrgId("INV-1", 1L)).thenReturn(false);
        InvestigationResponse resp = service.create(req("INV-1", "Title"));
        assertEquals("DRAFT", resp.getStatus());
        verify(investigationRepository).save(any());
        verify(graphReadinessService, never()).isOrgGraphReady(anyLong());
    }

    @Test
    void crossTenantIsolationCreation() {
        // orgId from auth is 1, never from request – verify service uses auth orgId
        when(investigationRepository.existsByInvestigationKeyAndOrganisationOrgId("INV-1", 1L)).thenReturn(false);
        service.create(req("INV-1", "Title"));
        verify(organisationRepository).getReferenceById(1L);
        verify(organisationRepository, never()).getReferenceById(2L);
    }

    @Test
    void noNeo4jAccessWhenGraphNotReady() {
        // Phase 2: creation does not query graph readiness at all
        when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(false);
        when(investigationRepository.existsByInvestigationKeyAndOrganisationOrgId("INV-1", 1L)).thenReturn(false);
        InvestigationResponse resp = service.create(req("INV-1", "Title"));
        assertEquals("DRAFT", resp.getStatus());
        verify(graphReadinessService, never()).isOrgGraphReady(anyLong());
        verify(investigationRepository).save(any());
    }

    // Transitions
    @Test
    void draftToActive() {
        Investigation inv = existing("DRAFT");
        when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(inv));
        InvestigationResponse resp = service.activate(100L);
        assertEquals("ACTIVE", resp.getStatus());
    }

    @Test
    void activeToCompleted() {
        Investigation inv = existing("ACTIVE");
        when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(inv));
        InvestigationResponse resp = service.complete(100L);
        assertEquals("COMPLETED", resp.getStatus());
    }

    @Test
    void completedToArchived() {
        Investigation inv = existing("COMPLETED");
        when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(inv));
        InvestigationResponse resp = service.archive(100L);
        assertEquals("ARCHIVED", resp.getStatus());
    }

    @Test
    void invalidDraftToCompleted409() {
        Investigation inv = existing("DRAFT");
        when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(inv));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.complete(100L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void invalidActiveToDraft409() {
        Investigation inv = existing("ACTIVE");
        when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(inv));
        // Try to activate again (requires DRAFT) -> should fail because already ACTIVE
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.activate(100L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void invalidCompletedToActive409() {
        Investigation inv = existing("COMPLETED");
        when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(inv));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.activate(100L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void archivedCannotTransition() {
        Investigation inv = existing("ARCHIVED");
        when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(inv));
        assertThrows(ResponseStatusException.class, () -> service.activate(100L));
        assertThrows(ResponseStatusException.class, () -> service.complete(100L));
        assertThrows(ResponseStatusException.class, () -> service.archive(100L));
    }

    @Test
    void missingInvestigation404() {
        when(investigationRepository.findByIdAndOrganisationOrgId(999L, 1L)).thenReturn(Optional.empty());
        when(investigationRepository.findById(999L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.activate(999L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void crossTenantInvestigation404() {
        // investigation belongs to org2, user is org1
        when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.empty());
        when(investigationRepository.findById(100L)).thenReturn(Optional.of(Investigation.builder().id(100L).organisation(org2).build()));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.activate(100L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        // ensure not revealing existence via 403
        assertFalse(ex.getMessage().contains("belongs to your organisation"));
    }

    @Test
    void graphNotReadyTransition400() {
        Investigation inv = existing("DRAFT");
        when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(inv));
        when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(false);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.activate(100L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void permissionDeniedTransition() {
        Investigation inv = existing("DRAFT");
        when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(inv));
        doThrow(new AccessDeniedException("Missing")).when(authorizationService).requireEvidenceGraphAccess();
        assertThrows(AccessDeniedException.class, () -> service.activate(100L));
    }

    @Test
    void orgIdAlwaysFromAuthorizationService() {
        // Change auth org to 2, create should use 2
        when(authorizationService.getCurrentOrgId()).thenReturn(2L);
        when(authorizationService.getCurrentUser()).thenReturn(User.builder().id(20L).organisation(org2).build());
        when(organisationRepository.getReferenceById(2L)).thenReturn(org2);
        when(graphReadinessService.isOrgGraphReady(2L)).thenReturn(true);
        when(investigationRepository.existsByInvestigationKeyAndOrganisationOrgId("INV-1", 2L)).thenReturn(false);
        service.create(req("INV-1", "Title"));
        verify(organisationRepository).getReferenceById(2L);
        verify(investigationRepository).existsByInvestigationKeyAndOrganisationOrgId("INV-1", 2L);
    }

    @Test
    void noNeo4jAccessWhenGraphNotReadyTransition() {
        Investigation inv = existing("DRAFT");
        when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(inv));
        when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(false);
        assertThrows(ResponseStatusException.class, () -> service.activate(100L));
        // ensure no save happened
        verify(investigationRepository, never()).save(any());
    }
}
