package com.taceiq;

import com.taceiq.dto.InvestigationEvidenceResponse;
import com.taceiq.dto.InvestigationEvidenceAssessmentRequest;
import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.Investigation;
import com.taceiq.entity.Organisation;
import com.taceiq.entity.User;
import com.taceiq.graph.service.GraphReadinessService;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.InvestigationEvidenceRepository;
import com.taceiq.repository.InvestigationRepository;
import com.taceiq.repository.InvestigationEvidenceAssessmentRepository;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.InvestigationEvidenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class InvestigationEvidenceServiceTest {

    @Mock InvestigationRepository investigationRepository;
    @Mock CanonicalEvidenceRepository canonicalRepo;
    @Mock InvestigationEvidenceRepository linkRepository;
    @Mock AuthorizationService authService;
    @Mock GraphReadinessService graphReadinessService;
    @Mock InvestigationEvidenceAssessmentRepository assessmentRepository;

    InvestigationEvidenceService service;

    Organisation org1 = Organisation.builder().orgId(1L).name("Org1").build();
    Organisation org2 = Organisation.builder().orgId(2L).name("Org2").build();
    User user1 = User.builder().id(10L).username("user1").organisation(org1).build();
    Investigation invDraft = Investigation.builder().id(100L).organisation(org1).investigationKey("INV-1").title("T").status("DRAFT").createdBy(user1).build();
    Investigation invCompleted = Investigation.builder().id(101L).organisation(org1).investigationKey("INV-2").title("T2").status("COMPLETED").createdBy(user1).build();
    Investigation invArchived = Investigation.builder().id(102L).organisation(org1).investigationKey("INV-3").title("T3").status("ARCHIVED").createdBy(user1).build();
    CanonicalEvidence ev = CanonicalEvidence.builder().id(200L).organisation(org1).externalId("ev_001").title("title ev_001").sourceType("FILE").status("READY").contentHash("h").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).build();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new InvestigationEvidenceService(investigationRepository, canonicalRepo, linkRepository, authService, graphReadinessService, null, null, assessmentRepository);
        lenient().when(authService.getCurrentOrgId()).thenReturn(1L);
        lenient().when(authService.getCurrentUser()).thenReturn(user1);
        lenient().doNothing().when(authService).requireEvidenceGraphAccess();
        lenient().when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(true);
        lenient().when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(invDraft));
        lenient().when(canonicalRepo.findByExternalIdAndOrganisationOrgId("ev_001", 1L)).thenReturn(Optional.of(ev));
        lenient().when(linkRepository.save(any())).thenAnswer(i -> {
            var link = (com.taceiq.entity.InvestigationEvidence) i.getArgument(0);
            link.setId(300L);
            return link;
        });
    }

    @Test
    void validLink() {
        when(linkRepository.existsByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(1L, 100L, 200L)).thenReturn(false);
        InvestigationEvidenceResponse resp = service.linkEvidence(100L, "ev_001");
        assertEquals("ev_001", resp.getStableId());
        assertEquals("title ev_001", resp.getTitle());
        verify(linkRepository).save(any());
    }

    @Test
    void duplicateLink409() {
        when(linkRepository.existsByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(1L, 100L, 200L)).thenReturn(true);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.linkEvidence(100L, "ev_001"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void missingEvidence404() {
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("missing", 1L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.linkEvidence(100L, "missing"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deletedEvidence404() {
        CanonicalEvidence deleted = CanonicalEvidence.builder().id(200L).organisation(org1).externalId("ev_001").isDeleted(true).build();
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("ev_001", 1L)).thenReturn(Optional.of(deleted));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.linkEvidence(100L, "ev_001"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void crossTenantEvidence404() {
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("ev_001", 1L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.linkEvidence(100L, "ev_001"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(canonicalRepo).findByExternalIdAndOrganisationOrgId("ev_001", 1L);
        verify(canonicalRepo, never()).findByExternalIdAndOrganisationOrgId("ev_001", 2L);
    }

    @Test
    void missingInvestigation404() {
        when(investigationRepository.findByIdAndOrganisationOrgId(999L, 1L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.linkEvidence(999L, "ev_001"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void crossTenantInvestigation404() {
        when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.linkEvidence(100L, "ev_001"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void graphNotReadyLink400() {
        when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(false);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.linkEvidence(100L, "ev_001"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(linkRepository, never()).save(any());
        // verify no Neo4j access via graphReadinessService was called but no further
        verify(graphReadinessService).isOrgGraphReady(1L);
    }

    @Test
    void permissionDeniedLink403() {
        doThrow(new AccessDeniedException("Missing")).when(authService).requireEvidenceGraphAccess();
        assertThrows(AccessDeniedException.class, () -> service.linkEvidence(100L, "ev_001"));
    }

    @Test
    void completedInvestigationRejected409() {
        when(investigationRepository.findByIdAndOrganisationOrgId(101L, 1L)).thenReturn(Optional.of(invCompleted));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.linkEvidence(101L, "ev_001"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void archivedInvestigationRejected409() {
        when(investigationRepository.findByIdAndOrganisationOrgId(102L, 1L)).thenReturn(Optional.of(invArchived));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.linkEvidence(102L, "ev_001"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void unlinkValid() {
        CanonicalEvidence ev2 = ev;
        var link = com.taceiq.entity.InvestigationEvidence.builder().id(300L).organisation(org1).investigation(invDraft).canonicalEvidence(ev2).build();
        when(linkRepository.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(1L, 100L, 200L)).thenReturn(Optional.of(link));
        assertDoesNotThrow(() -> service.unlinkEvidence(100L, "ev_001"));
        verify(linkRepository).delete(link);
        // canonical remains
        verify(canonicalRepo, never()).delete(any());
    }

    @Test
    void unlinkMissing404() {
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("ev_001", 1L)).thenReturn(Optional.of(ev));
        when(linkRepository.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(1L, 100L, 200L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.unlinkEvidence(100L, "ev_001"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void canonicalRemainsAfterUnlink() {
        var link = com.taceiq.entity.InvestigationEvidence.builder().id(300L).organisation(org1).investigation(invDraft).canonicalEvidence(ev).build();
        when(linkRepository.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(1L, 100L, 200L)).thenReturn(Optional.of(link));
        service.unlinkEvidence(100L, "ev_001");
        verify(linkRepository).delete(link);
        verify(canonicalRepo, never()).delete(any(CanonicalEvidence.class));
    }

    @Test
    void boundedPagination() {
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of(
                com.taceiq.entity.InvestigationEvidence.builder().id(1L).organisation(org1).investigation(invDraft).canonicalEvidence(ev).build()
        )));
        var page = service.listEvidence(100L, 0, 20);
        assertEquals(1, page.getTotalElements());
        // test max 100
        assertThrows(ResponseStatusException.class, () -> service.listEvidence(100L, 0, 101));
    }

    @Test
    void tenantIsolationList() {
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of(
                com.taceiq.entity.InvestigationEvidence.builder().id(1L).organisation(org1).investigation(invDraft).canonicalEvidence(ev).build()
        )));
        service.listEvidence(100L, 0, 20);
        verify(linkRepository).findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any());
        verify(linkRepository, never()).findByOrganisationOrgIdAndInvestigationId(eq(2L), anyLong(), any());
    }

    @Test
    void assessmentIsCreatedAndReturnedForInvestigationEvidence() {
        ev.setIncident(invDraft);
        String originalPayload = ev.getNormalizedPayload();
        String originalHash = ev.getContentHash();
        InvestigationEvidenceAssessmentRequest request = new InvestigationEvidenceAssessmentRequest();
        request.setReviewStatus("REVIEWED");
        request.setRelevance("RELEVANT");
        request.setImportance("HIGH");
        request.setAssessment("SUPPORTS_INVESTIGATION");
        request.setInvestigatorNotes("Source-supported note");
        var persisted = new com.taceiq.entity.InvestigationEvidenceAssessment[1];
        when(assessmentRepository.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(1L, 100L, 200L))
            .thenAnswer(invocation -> Optional.ofNullable(persisted[0]));
        when(assessmentRepository.save(any())).thenAnswer(invocation -> {
            persisted[0] = invocation.getArgument(0);
            return persisted[0];
        });

        InvestigationEvidenceResponse response = service.saveAssessment(100L, "ev_001", request);

        assertEquals("REVIEWED", response.getReviewStatus());
        assertEquals("DIRECT", response.getRelevance());
        assertEquals("RELEVANT", response.getAssessmentRelevance());
        assertEquals("HIGH", response.getImportance());
        assertEquals("SUPPORTS_INVESTIGATION", response.getAssessment());
        assertEquals(10L, response.getReviewedByUserId());
        assertNotNull(response.getReviewedAt());
        assertEquals(originalPayload, ev.getNormalizedPayload());
        assertEquals(originalHash, ev.getContentHash());
        verify(assessmentRepository).save(any());
    }

    @Test
    void assessmentUpdateReusesExistingRow() {
        ev.setIncident(invDraft);
        var existing = com.taceiq.entity.InvestigationEvidenceAssessment.builder()
                .id(400L).organisation(org1).investigation(invDraft).canonicalEvidence(ev).reviewStatus("REVIEWED").build();
        when(assessmentRepository.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(1L, 100L, 200L)).thenReturn(Optional.of(existing));
        when(assessmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        InvestigationEvidenceAssessmentRequest request = new InvestigationEvidenceAssessmentRequest();
        request.setReviewStatus("PENDING_REVIEW");

        service.saveAssessment(100L, "ev_001", request);

        verify(assessmentRepository).save(same(existing));
        assertEquals(400L, existing.getId());
        assertEquals("PENDING_REVIEW", existing.getReviewStatus());
    }

    @Test
    void reviewedAssessmentRequiresClassifications() {
        ev.setIncident(invDraft);
        InvestigationEvidenceAssessmentRequest request = new InvestigationEvidenceAssessmentRequest();
        request.setReviewStatus("REVIEWED");
        request.setRelevance("RELEVANT");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.saveAssessment(100L, "ev_001", request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(assessmentRepository, never()).save(any());
    }

    @Test
    void assessmentCannotAccessEvidenceFromAnotherInvestigation() {
        Investigation other = Investigation.builder().id(999L).organisation(org1).status("DRAFT").build();
        ev.setIncident(other);
        InvestigationEvidenceAssessmentRequest request = new InvestigationEvidenceAssessmentRequest();
        request.setReviewStatus("UNREVIEWED");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.saveAssessment(100L, "ev_001", request));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(assessmentRepository, never()).save(any());
    }

    @Test
    void invalidAssessmentValueIsRejected() {
        ev.setIncident(invDraft);
        InvestigationEvidenceAssessmentRequest request = new InvestigationEvidenceAssessmentRequest();
        request.setReviewStatus("REVIEWED");
        request.setRelevance("RELEVANT");
        request.setImportance("HIGH");
        request.setAssessment("MADE_UP_VALUE");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.saveAssessment(100L, "ev_001", request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(assessmentRepository, never()).save(any());
    }

    @Test
    void permissionDeniedAssessmentIsRejected() {
        doThrow(new AccessDeniedException("Missing")).when(authService).requireEvidenceGraphAccess();
        InvestigationEvidenceAssessmentRequest request = new InvestigationEvidenceAssessmentRequest();
        assertThrows(AccessDeniedException.class, () -> service.saveAssessment(100L, "ev_001", request));
        verify(assessmentRepository, never()).save(any());
    }
}
