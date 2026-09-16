package com.taceiq;

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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class InvestigationReadTest {

    @Mock InvestigationRepository investigationRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock AuthorizationService authService;
    @Mock GraphReadinessService graphReadinessService;

    InvestigationService service;

    Organisation org1 = Organisation.builder().orgId(1L).name("Org1").build();
    Organisation org2 = Organisation.builder().orgId(2L).name("Org2").build();
    User user1 = User.builder().id(10L).username("user1").organisation(org1).build();

    Investigation invDraft = Investigation.builder().id(100L).organisation(org1).investigationKey("INV-DRAFT").title("Draft").status("DRAFT").createdAt(Instant.parse("2026-09-01T10:00:00Z")).build();
    Investigation invActive = Investigation.builder().id(101L).organisation(org1).investigationKey("INV-ACTIVE").title("Active").status("ACTIVE").createdAt(Instant.parse("2026-09-02T10:00:00Z")).build();
    Investigation invCompleted = Investigation.builder().id(102L).organisation(org1).investigationKey("INV-COMPLETED").title("Completed").status("COMPLETED").createdAt(Instant.parse("2026-09-03T10:00:00Z")).build();
    Investigation invArchived = Investigation.builder().id(103L).organisation(org1).investigationKey("INV-ARCHIVED").title("Archived").status("ARCHIVED").createdAt(Instant.parse("2026-09-04T10:00:00Z")).build();
    Investigation invOtherOrg = Investigation.builder().id(200L).organisation(org2).investigationKey("INV-OTHER").title("Other").status("ACTIVE").createdAt(Instant.now()).build();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new InvestigationService(investigationRepository, organisationRepository, authService, graphReadinessService);
        lenient().when(authService.getCurrentOrgId()).thenReturn(1L);
        lenient().doNothing().when(authService).requireEvidenceGraphAccess();
    }

    // List tests
    @Test
    void listAuthenticatedOrgCanListItsInvestigations() {
        Page<Investigation> page = new PageImpl<>(List.of(invDraft, invActive));
        when(investigationRepository.findByOrganisationOrgId(eq(1L), any())).thenReturn(page);
        var result = service.listInvestigations(PageRequest.of(0, 20));
        assertEquals(2, result.getTotalElements());
        verify(authService).requireEvidenceGraphAccess();
        verify(investigationRepository).findByOrganisationOrgId(eq(1L), any());
    }

    @Test
    void listPaginationWorks() {
        Page<Investigation> page = new PageImpl<>(List.of(invActive), PageRequest.of(1, 1), 2);
        when(investigationRepository.findByOrganisationOrgId(eq(1L), any())).thenReturn(page);
        var result = service.listInvestigations(PageRequest.of(1, 1));
        assertEquals(1, result.getNumber());
        assertEquals(1, result.getSize());
    }

    @Test
    void listMultipleStatusesReturned() {
        Page<Investigation> page = new PageImpl<>(List.of(invDraft, invActive, invCompleted, invArchived));
        when(investigationRepository.findByOrganisationOrgId(eq(1L), any())).thenReturn(page);
        var result = service.listInvestigations(PageRequest.of(0, 20));
        assertEquals(4, result.getTotalElements());
        assertTrue(result.getContent().stream().anyMatch(r -> r.getStatus().equals("DRAFT")));
        assertTrue(result.getContent().stream().anyMatch(r -> r.getStatus().equals("ARCHIVED")));
    }

    @Test
    void listExcludesOtherOrganisation() {
        Page<Investigation> page = new PageImpl<>(List.of(invDraft));
        when(investigationRepository.findByOrganisationOrgId(eq(1L), any())).thenReturn(page);
        var result = service.listInvestigations(PageRequest.of(0, 20));
        assertEquals(1, result.getTotalElements());
        verify(investigationRepository).findByOrganisationOrgId(eq(1L), any());
        verify(investigationRepository, never()).findByOrganisationOrgId(eq(2L), any());
    }

    @Test
    void listDeterministicOrdering() {
        // Verify service applies deterministic sort when unsorted
        Page<Investigation> page = new PageImpl<>(List.of(invDraft));
        when(investigationRepository.findByOrganisationOrgId(eq(1L), any())).thenReturn(page);
        service.listInvestigations(PageRequest.of(0, 20));
        verify(investigationRepository).findByOrganisationOrgId(eq(1L), argThat(p -> {
            Sort s = p.getSort();
            return s.getOrderFor("createdAt") != null && s.getOrderFor("createdAt").getDirection().isDescending()
                    && s.getOrderFor("id") != null;
        }));
    }

    @Test
    void listAuthorizationWorks() {
        doThrow(new AccessDeniedException("Missing")).when(authService).requireEvidenceGraphAccess();
        assertThrows(AccessDeniedException.class, () -> service.listInvestigations(PageRequest.of(0, 20)));
    }

    // Get tests
    @Test
    void getSameOrgReturns200() {
        when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(invDraft));
        InvestigationResponse resp = service.getInvestigation(100L);
        assertEquals(100L, resp.getId());
        assertEquals("INV-DRAFT", resp.getInvestigationKey());
    }

    @Test
    void getMissingReturns404() {
        when(investigationRepository.findByIdAndOrganisationOrgId(999L, 1L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getInvestigation(999L));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void getCrossOrgReturns404() {
        when(investigationRepository.findByIdAndOrganisationOrgId(200L, 1L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getInvestigation(200L));
        assertEquals(404, ex.getStatusCode().value());
        // Ensure not leaking existence via 403
        assertFalse(ex.getMessage().contains("belongs to your organisation"));
    }

    @Test
    void getAuthorizationWorks() {
        doThrow(new AccessDeniedException("Missing")).when(authService).requireEvidenceGraphAccess();
        assertThrows(AccessDeniedException.class, () -> service.getInvestigation(100L));
    }

    @Test
    void existingLifecycleTestsRemainPassing() {
        // Ensure activate still works (existing behavior)
        Investigation inv = Investigation.builder().id(100L).organisation(org1).investigationKey("INV-1").title("T").status("DRAFT").build();
        when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(inv));
        when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(true);
        when(investigationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var resp = service.activate(100L);
        assertEquals("ACTIVE", resp.getStatus());
    }
}
