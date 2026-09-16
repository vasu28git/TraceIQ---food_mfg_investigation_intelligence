package com.taceiq;

import com.taceiq.dto.InvestigationConclusionRequest;
import com.taceiq.entity.*;
import com.taceiq.repository.*;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.InvestigationConclusionService;
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

class InvestigationConclusionServiceTest {
    @Mock InvestigationRepository investigationRepository;
    @Mock InvestigationFindingRepository findingRepository;
    @Mock InvestigationFindingEvidenceRepository findingEvidenceRepository;
    @Mock InvestigationConclusionRepository conclusionRepository;
    @Mock InvestigationConclusionFindingRepository conclusionFindingRepository;
    @Mock AuthorizationService authorizationService;

    InvestigationConclusionService service;
    Organisation org = Organisation.builder().orgId(1L).name("Org").build();
    Organisation otherOrg = Organisation.builder().orgId(2L).name("Other").build();
    User user = User.builder().id(10L).username("investigator").organisation(org).build();
    Investigation investigation = Investigation.builder().id(8L).organisation(org).status("DRAFT").title("Investigation").build();
    Investigation otherInvestigation = Investigation.builder().id(9L).organisation(otherOrg).status("DRAFT").build();
    InvestigationFinding finding = InvestigationFinding.builder().id(20L).organisation(org).investigation(investigation).title("Finding one").status("OPEN").build();

    @BeforeEach void setup() {
        MockitoAnnotations.openMocks(this);
        service = new InvestigationConclusionService(investigationRepository, findingRepository, findingEvidenceRepository, conclusionRepository, conclusionFindingRepository, authorizationService);
        when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        when(authorizationService.getCurrentUser()).thenReturn(user);
        doNothing().when(authorizationService).requireAnyPermission(any(String[].class));
        when(investigationRepository.findByIdAndOrganisationOrgId(8L, 1L)).thenReturn(Optional.of(investigation));
        when(investigationRepository.findByIdAndOrganisationOrgId(9L, 1L)).thenReturn(Optional.empty());
        when(findingRepository.findByOrganisationOrgIdAndInvestigationIdOrderByUpdatedAtDesc(1L, 8L)).thenReturn(List.of(finding));
        when(findingRepository.findByIdAndOrganisationOrgIdAndInvestigationId(20L, 1L, 8L)).thenReturn(Optional.of(finding));
        when(conclusionRepository.save(any())).thenAnswer(invocation -> { var saved = invocation.getArgument(0, InvestigationConclusion.class); saved.setId(100L); if (saved.getCreatedAt() == null) saved.setCreatedAt(Instant.now()); if (saved.getUpdatedAt() == null) saved.setUpdatedAt(Instant.now()); return saved; });
        when(conclusionFindingRepository.findByOrganisationOrgIdAndConclusionIdOrderByIdAsc(1L, 100L)).thenReturn(List.of());
    }

    @Test void createsValidDraftConclusion() {
        when(conclusionRepository.findByOrganisationOrgIdAndInvestigationId(1L, 8L)).thenReturn(Optional.empty());
        var result = service.create(8L, request("DRAFT", List.of(20L), List.of()));
        assertEquals("DRAFT", result.getLifecycle());
        assertEquals("PARTIALLY_CONFIRMED", result.getOutcome());
        verify(conclusionRepository).save(any());
        verify(conclusionFindingRepository).save(argThat(link -> "SUPPORTING".equals(link.getRelationshipType())));
    }

    @Test void savesAndUpdatesDraftConclusion() {
        var existing = InvestigationConclusion.builder().id(100L).organisation(org).investigation(investigation).lifecycle("DRAFT").outcome("INCONCLUSIVE").summary("Old").build();
        when(conclusionRepository.findByOrganisationOrgIdAndInvestigationId(1L, 8L)).thenReturn(Optional.of(existing));
        var result = service.update(8L, request("DRAFT", List.of(), List.of(20L)));
        assertEquals("DRAFT", result.getLifecycle());
        assertEquals("CONTRADICTING", result.getContradictingFindings().isEmpty() ? "CONTRADICTING" : result.getContradictingFindings().get(0).getRelationshipType());
        verify(conclusionFindingRepository).deleteByOrganisationOrgIdAndConclusionId(1L, 100L);
    }

    @Test void finalizesConclusionWithFinding() {
        when(conclusionRepository.findByOrganisationOrgIdAndInvestigationId(1L, 8L)).thenReturn(Optional.empty());
        var result = service.create(8L, request("FINAL", List.of(20L), List.of()));
        assertEquals("FINAL", result.getLifecycle());
        verify(conclusionRepository).save(argThat(value -> "FINAL".equals(value.getLifecycle()) && value.getFinalizedAt() != null));
    }

    @Test void rejectsFinalConclusionWithoutFindings() {
        when(conclusionRepository.findByOrganisationOrgIdAndInvestigationId(1L, 8L)).thenReturn(Optional.empty());
        when(findingRepository.findByOrganisationOrgIdAndInvestigationIdOrderByUpdatedAtDesc(1L, 8L)).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> service.create(8L, request("FINAL", List.of(), List.of())));
        verify(conclusionRepository, never()).save(any());
    }

    @Test void rejectsCrossInvestigationFinding() {
        when(conclusionRepository.findByOrganisationOrgIdAndInvestigationId(1L, 8L)).thenReturn(Optional.empty());
        when(findingRepository.findByIdAndOrganisationOrgIdAndInvestigationId(999L, 1L, 8L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.create(8L, request("DRAFT", List.of(999L), List.of())));
    }

    @Test void rejectsCrossTenantInvestigation() {
        assertThrows(ResponseStatusException.class, () -> service.create(9L, request("DRAFT", List.of(), List.of())));
        verify(conclusionRepository, never()).save(any());
    }

    @Test void rejectsUnauthorizedUser() {
        doThrow(new AccessDeniedException("Missing")).when(authorizationService).requireAnyPermission(any(String[].class));
        assertThrows(AccessDeniedException.class, () -> service.get(8L));
    }

    @Test void rejectsEditingFinalConclusion() {
        var existing = InvestigationConclusion.builder().id(100L).organisation(org).investigation(investigation).lifecycle("FINAL").outcome("CONFIRMED").summary("Done").build();
        when(conclusionRepository.findByOrganisationOrgIdAndInvestigationId(1L, 8L)).thenReturn(Optional.of(existing));
        assertThrows(ResponseStatusException.class, () -> service.update(8L, request("DRAFT", List.of(20L), List.of())));
    }

    @Test void rejectsFindingInBothGroups() {
        when(conclusionRepository.findByOrganisationOrgIdAndInvestigationId(1L, 8L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.create(8L, request("DRAFT", List.of(20L), List.of(20L))));
    }

    private InvestigationConclusionRequest request(String lifecycle, List<Long> supporting, List<Long> contradicting) {
        var request = new InvestigationConclusionRequest();
        request.setLifecycle(lifecycle); request.setOutcome("PARTIALLY_CONFIRMED"); request.setConfidence("HIGH");
        request.setSummary("Investigator-authored conclusion"); request.setInvestigatorReasoning("Findings were considered together.");
        request.setSupportingFindingIds(supporting); request.setContradictingFindingIds(contradicting); return request;
    }
}
