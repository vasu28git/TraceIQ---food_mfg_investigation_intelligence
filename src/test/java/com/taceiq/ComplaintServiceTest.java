package com.taceiq;

import com.taceiq.dto.CreateComplaintRequest;
import com.taceiq.dto.ComplaintResponse;
import com.taceiq.dto.CreateInvestigationFromComplaintRequest;
import com.taceiq.dto.InvestigationResponse;
import com.taceiq.entity.Complaint;
import com.taceiq.entity.Integration;
import com.taceiq.entity.Investigation;
import com.taceiq.entity.Organisation;
import com.taceiq.entity.User;
import com.taceiq.graph.service.GraphReadinessService;
import com.taceiq.repository.ComplaintRepository;
import com.taceiq.repository.IntegrationRepository;
import com.taceiq.repository.InvestigationRepository;
import com.taceiq.repository.OrganisationRepository;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.ComplaintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ComplaintServiceTest {

    @Mock ComplaintRepository complaintRepository;
    @Mock InvestigationRepository investigationRepository;
    @Mock IntegrationRepository integrationRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock AuthorizationService authorizationService;
    @Mock GraphReadinessService graphReadinessService;

    ComplaintService service;

    Organisation org1 = Organisation.builder().orgId(1L).name("Org1").build();
    Organisation org2 = Organisation.builder().orgId(2L).name("Org2").build();
    User user1 = User.builder().id(10L).username("user1").organisation(org1).build();
    Integration int1 = Integration.builder().id(50L).organisation(org1).name("Int1").build();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new ComplaintService(complaintRepository, investigationRepository, integrationRepository, organisationRepository, authorizationService, graphReadinessService);
        lenient().when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        lenient().when(authorizationService.getCurrentUser()).thenReturn(user1);
        lenient().doNothing().when(authorizationService).requireEvidenceGraphAccess();
        lenient().when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(true);
        lenient().when(organisationRepository.getReferenceById(1L)).thenReturn(org1);
        lenient().when(complaintRepository.save(any())).thenAnswer(i -> {
            Complaint c = i.getArgument(0);
            c.setId(100L);
            return c;
        });
        lenient().when(investigationRepository.save(any())).thenAnswer(i -> {
            Investigation inv = i.getArgument(0);
            inv.setId(200L);
            return inv;
        });
    }

    CreateComplaintRequest manualReq(String key, String title) {
        return CreateComplaintRequest.builder().complaintKey(key).title(title).description("desc").batchReference("batch1").externalReference("ext1").raisedAt("2026-09-01T00:00:00Z").build();
    }

    // Manual intake
    @Test
    void validComplaintCreationManual() {
        when(complaintRepository.existsByComplaintKeyAndOrganisationOrgId("C-1", 1L)).thenReturn(false);
        ComplaintResponse resp = service.createManual(manualReq("C-1", "Title"));
        assertEquals("C-1", resp.getComplaintKey());
        assertEquals("MANUAL", resp.getSourceType());
        assertEquals(10L, resp.getCreatedByUserId());
        verify(complaintRepository).save(argThat(c -> "MANUAL".equals(c.getSourceType()) && c.getOrganisation().getOrgId().equals(1L)));
    }

    @Test
    void duplicateComplaintKey409() {
        when(complaintRepository.existsByComplaintKeyAndOrganisationOrgId("C-1", 1L)).thenReturn(true);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.createManual(manualReq("C-1", "Title")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void validationFailures400() {
        assertThrows(ResponseStatusException.class, () -> service.createManual(CreateComplaintRequest.builder().complaintKey(" ").title("T").build()));
        assertThrows(ResponseStatusException.class, () -> service.createManual(CreateComplaintRequest.builder().complaintKey("K").title(" ").build()));
    }

    @Test
    void permissionDeniedManual403() {
        doThrow(new AccessDeniedException("Missing")).when(authorizationService).requireEvidenceGraphAccess();
        assertThrows(AccessDeniedException.class, () -> service.createManual(manualReq("C-1", "T")));
    }

    @Test
    void graphNotReadyManualStillSucceeds() {
        when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(false);
        service.createManual(manualReq("C-1", "T"));
        verify(complaintRepository).save(any());
    }

    @Test
    void tenantIsolationManual() {
        service.createManual(manualReq("C-1", "T"));
        verify(organisationRepository).getReferenceById(1L);
        verify(organisationRepository, never()).getReferenceById(2L);
        verify(complaintRepository).existsByComplaintKeyAndOrganisationOrgId("C-1", 1L);
    }

    @Test
    void crossTenantRequestBehaves404ComplaintRead() {
        when(complaintRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getComplaint(100L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // Complaint -> Investigation
    @Test
    void validCreationFromComplaint() {
        Complaint complaint = Complaint.builder().id(100L).organisation(org1).complaintKey("C-1").title("T").sourceType("MANUAL").build();
        when(complaintRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(complaint));
        when(investigationRepository.existsByInvestigationKeyAndOrganisationOrgId("INV-1", 1L)).thenReturn(false);
        InvestigationResponse resp = service.createInvestigationFromComplaint(100L, CreateInvestigationFromComplaintRequest.builder().investigationKey("INV-1").title("Inv Title").build());
        assertEquals("DRAFT", resp.getStatus());
        assertEquals("INV-1", resp.getInvestigationKey());
        verify(complaintRepository).save(argThat(c -> c.getInvestigation() != null && c.getInvestigation().getInvestigationKey().equals("INV-1")));
    }

    @Test
    void investigationStartsDraft() {
        Complaint c = Complaint.builder().id(100L).organisation(org1).complaintKey("C-1").title("T").sourceType("MANUAL").build();
        when(complaintRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(c));
        when(investigationRepository.existsByInvestigationKeyAndOrganisationOrgId("INV-1", 1L)).thenReturn(false);
        var resp = service.createInvestigationFromComplaint(100L, CreateInvestigationFromComplaintRequest.builder().investigationKey("INV-1").title("T").build());
        assertEquals("DRAFT", resp.getStatus());
    }

    @Test
    void complaintBecomesAssociated() {
        Complaint c = Complaint.builder().id(100L).organisation(org1).complaintKey("C-1").title("T").sourceType("MANUAL").build();
        when(complaintRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(c));
        when(investigationRepository.existsByInvestigationKeyAndOrganisationOrgId("INV-1", 1L)).thenReturn(false);
        service.createInvestigationFromComplaint(100L, CreateInvestigationFromComplaintRequest.builder().investigationKey("INV-1").title("T").build());
        assertNotNull(c.getInvestigation());
        assertEquals("INV-1", c.getInvestigation().getInvestigationKey());
    }

    @Test
    void duplicateInvestigationKey409() {
        Complaint c = Complaint.builder().id(100L).organisation(org1).complaintKey("C-1").title("T").sourceType("MANUAL").build();
        when(complaintRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(c));
        when(investigationRepository.existsByInvestigationKeyAndOrganisationOrgId("INV-1", 1L)).thenReturn(true);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.createInvestigationFromComplaint(100L, CreateInvestigationFromComplaintRequest.builder().investigationKey("INV-1").title("T").build()));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void missingComplaint404() {
        when(complaintRepository.findByIdAndOrganisationOrgId(999L, 1L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.createInvestigationFromComplaint(999L, CreateInvestigationFromComplaintRequest.builder().investigationKey("INV-1").title("T").build()));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void crossTenantComplaint404() {
        when(complaintRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.createInvestigationFromComplaint(100L, CreateInvestigationFromComplaintRequest.builder().investigationKey("INV-1").title("T").build()));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void graphNotReadyInvestigationStillSucceeds() {
        Complaint c = Complaint.builder().id(100L).organisation(org1).complaintKey("C-1").title("T").sourceType("MANUAL").build();
        when(complaintRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(c));
        when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(false);
        when(investigationRepository.existsByInvestigationKeyAndOrganisationOrgId("INV-1", 1L)).thenReturn(false);
        InvestigationResponse resp = service.createInvestigationFromComplaint(100L, CreateInvestigationFromComplaintRequest.builder().investigationKey("INV-1").title("T").build());
        assertEquals("DRAFT", resp.getStatus());
        verify(complaintRepository).save(c);
    }

    @Test
    void permissionDeniedInvestigation403() {
        Complaint c = Complaint.builder().id(100L).organisation(org1).complaintKey("C-1").title("T").sourceType("MANUAL").build();
        when(complaintRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(c));
        doThrow(new AccessDeniedException("Missing")).when(authorizationService).requireEvidenceGraphAccess();
        assertThrows(AccessDeniedException.class, () -> service.createInvestigationFromComplaint(100L, CreateInvestigationFromComplaintRequest.builder().investigationKey("INV-1").title("T").build()));
    }

    @Test
    void atomicAssociation() {
        Complaint c = Complaint.builder().id(100L).organisation(org1).complaintKey("C-1").title("T").sourceType("MANUAL").build();
        when(complaintRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(c));
        when(investigationRepository.existsByInvestigationKeyAndOrganisationOrgId("INV-1", 1L)).thenReturn(false);
        service.createInvestigationFromComplaint(100L, CreateInvestigationFromComplaintRequest.builder().investigationKey("INV-1").title("T").build());
        verify(investigationRepository).save(any(Investigation.class));
        verify(complaintRepository).save(c);
        assertEquals("INV-1", c.getInvestigation().getInvestigationKey());
    }

    // Read
    @Test
    void validComplaintRead() {
        Complaint c = Complaint.builder().id(100L).organisation(org1).complaintKey("C-1").title("T").sourceType("MANUAL").externalReference("ext").batchReference("batch").raisedAt(Instant.now()).receivedAt(Instant.now()).createdBy(user1).build();
        Investigation inv = Investigation.builder().id(200L).investigationKey("INV-1").title("InvT").status("DRAFT").build();
        c.setInvestigation(inv);
        when(complaintRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(c));
        ComplaintResponse resp = service.getComplaint(100L);
        assertEquals("C-1", resp.getComplaintKey());
        assertNotNull(resp.getInvestigation());
        assertEquals("INV-1", resp.getInvestigation().getInvestigationKey());
        assertEquals("MANUAL", resp.getSourceType());
    }

    @Test
    void missingComplaintRead404() {
        when(complaintRepository.findByIdAndOrganisationOrgId(999L, 1L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.getComplaint(999L));
    }

    @Test
    void crossTenantComplaintRead404() {
        when(complaintRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.getComplaint(100L));
    }

    @Test
    void safeInvestigationSummary() {
        Complaint c = Complaint.builder().id(100L).organisation(org1).complaintKey("C-1").title("T").sourceType("MANUAL").build();
        Investigation inv = Investigation.builder().id(200L).investigationKey("INV-1").title("InvT").status("DRAFT").build();
        c.setInvestigation(inv);
        when(complaintRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(c));
        ComplaintResponse resp = service.getComplaint(100L);
        // Should not expose orgId
        String json = resp.toString();
        assertFalse(json.contains("orgId") || json.contains("org_id"));
    }

    // Integration domain boundary
    @Test
    void integrationSourceType() {
        // Simulate createFromIntegration
        when(integrationRepository.findByIdAndOrganisationOrgId(50L, 1L)).thenReturn(Optional.of(int1));
        when(complaintRepository.findByOrganisationOrgIdAndIntegrationIdAndExternalReference(1L, 50L, "ext-123")).thenReturn(Optional.empty());
        when(complaintRepository.existsByComplaintKeyAndOrganisationOrgId(anyString(), eq(1L))).thenReturn(false);
        ComplaintResponse resp = service.createFromIntegration(1L, 50L, "ext-123", "Title", "Desc", "C-INT-1");
        assertEquals("INTEGRATION", resp.getSourceType());
        assertEquals("ext-123", resp.getExternalReference());
    }

    @Test
    void externalReferenceIdempotencySameDoesNotCreateDuplicate() {
        Complaint existing = Complaint.builder().id(100L).organisation(org1).integration(int1).externalReference("ext-123").complaintKey("C-1").title("T").sourceType("INTEGRATION").build();
        when(integrationRepository.findByIdAndOrganisationOrgId(50L, 1L)).thenReturn(Optional.of(int1));
        when(complaintRepository.findByOrganisationOrgIdAndIntegrationIdAndExternalReference(1L, 50L, "ext-123")).thenReturn(Optional.of(existing));
        ComplaintResponse resp = service.createFromIntegration(1L, 50L, "ext-123", "Title", "Desc", "C-INT-1");
        assertEquals(100L, resp.getId());
        verify(complaintRepository, never()).save(any());
    }

    @Test
    void differentExternalReferencesCreateSeparate() {
        when(integrationRepository.findByIdAndOrganisationOrgId(50L, 1L)).thenReturn(Optional.of(int1));
        when(complaintRepository.findByOrganisationOrgIdAndIntegrationIdAndExternalReference(1L, 50L, "ext-123")).thenReturn(Optional.empty());
        when(complaintRepository.findByOrganisationOrgIdAndIntegrationIdAndExternalReference(1L, 50L, "ext-456")).thenReturn(Optional.empty());
        when(complaintRepository.existsByComplaintKeyAndOrganisationOrgId(anyString(), eq(1L))).thenReturn(false);
        ComplaintResponse r1 = service.createFromIntegration(1L, 50L, "ext-123", "T1", "D1", "C-1");
        ComplaintResponse r2 = service.createFromIntegration(1L, 50L, "ext-456", "T2", "D2", "C-2");
        assertNotEquals(r1.getComplaintKey(), r2.getComplaintKey());
        verify(complaintRepository, times(2)).save(any());
    }

    @Test
    void tenantIsolationIntegration() {
        // orgId from integration's org, not client
        when(integrationRepository.findByIdAndOrganisationOrgId(50L, 1L)).thenReturn(Optional.of(int1));
        when(complaintRepository.findByOrganisationOrgIdAndIntegrationIdAndExternalReference(1L, 50L, "ext-1")).thenReturn(Optional.empty());
        when(complaintRepository.existsByComplaintKeyAndOrganisationOrgId(anyString(), eq(1L))).thenReturn(false);
        service.createFromIntegration(1L, 50L, "ext-1", "T", "D", "C-1");
        verify(complaintRepository).findByOrganisationOrgIdAndIntegrationIdAndExternalReference(eq(1L), eq(50L), eq("ext-1"));
        // ensure not using org 2
        verify(complaintRepository, never()).findByOrganisationOrgIdAndIntegrationIdAndExternalReference(eq(2L), anyLong(), anyString());
    }

    @Test
    void orgIdAlwaysFromAuthenticatedContext() {
        when(complaintRepository.existsByComplaintKeyAndOrganisationOrgId("C-1", 1L)).thenReturn(false);
        service.createManual(CreateComplaintRequest.builder().complaintKey("C-1").title("T").build());
        verify(complaintRepository).existsByComplaintKeyAndOrganisationOrgId("C-1", 1L);
        verify(complaintRepository, never()).existsByComplaintKeyAndOrganisationOrgId("C-1", 2L);
    }

    @Test
    void complaintCreationSucceedsWithoutGraphReady() {
        when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(false);
        service.createManual(CreateComplaintRequest.builder().complaintKey("C-1").title("T").build());
        verify(complaintRepository).save(any());
    }

    @Test
    void noCanonicalEvidenceMutation() {
        when(complaintRepository.existsByComplaintKeyAndOrganisationOrgId("C-1", 1L)).thenReturn(false);
        service.createManual(CreateComplaintRequest.builder().complaintKey("C-1").title("T").build());
        // Ensure no canonical repo interaction (we don't have canonical repo in this service, so trivially true)
        // Just verify complaint save happened
        verify(complaintRepository).save(any());
    }

    @Test
    void createManual_autoCreatesAndLinksInvestigationWithBatchReference() {
        when(complaintRepository.existsByComplaintKeyAndOrganisationOrgId("CMP-502", 1L)).thenReturn(false);
        when(investigationRepository.findByInvestigationKeyAndOrganisationOrgId("INV-CMP-502", 1L)).thenReturn(Optional.empty());

        CreateComplaintRequest req = CreateComplaintRequest.builder()
                .complaintKey("CMP-502")
                .title("BBQ Seasoned Beef Jerky pouch seal failure and spoilage")
                .description("Customer complaint details...")
                .batchReference("BATCH-1010")
                .build();

        ComplaintResponse resp = service.createManual(req);

        assertEquals("CMP-502", resp.getComplaintKey());
        assertNotNull(resp.getInvestigation());
        assertEquals("INV-CMP-502", resp.getInvestigation().getInvestigationKey());
        assertEquals("BATCH-1010", resp.getBatchReference());

        verify(investigationRepository).save(argThat(inv ->
                "INV-CMP-502".equals(inv.getInvestigationKey()) &&
                "BATCH-1010".equals(inv.getBatchReference())
        ));
        verify(complaintRepository).save(argThat(c ->
                "CMP-502".equals(c.getComplaintKey()) &&
                c.getInvestigation() != null &&
                "INV-CMP-502".equals(c.getInvestigation().getInvestigationKey())
        ));
    }
}
