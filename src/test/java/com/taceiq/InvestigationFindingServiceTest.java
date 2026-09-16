package com.taceiq;

import com.taceiq.dto.InvestigationFindingRequest;
import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.Investigation;
import com.taceiq.entity.InvestigationEvidenceAssessment;
import com.taceiq.entity.Organisation;
import com.taceiq.entity.User;
import com.taceiq.graph.service.GraphReadinessService;
import com.taceiq.repository.*;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.InvestigationFindingService;
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

class InvestigationFindingServiceTest {
    @Mock InvestigationRepository investigationRepository;
    @Mock InvestigationFindingRepository findingRepository;
    @Mock InvestigationFindingEvidenceRepository linkRepository;
    @Mock InvestigationEvidenceRepository investigationEvidenceRepository;
    @Mock CanonicalEvidenceRepository evidenceRepository;
    @Mock InvestigationEvidenceAssessmentRepository assessmentRepository;
    @Mock AuthorizationService authorizationService;
    InvestigationFindingService service;
    Organisation org = Organisation.builder().orgId(1L).name("Org").build();
    User user = User.builder().id(10L).username("investigator").organisation(org).build();
    Investigation investigation = Investigation.builder().id(8L).organisation(org).status("DRAFT").build();
    CanonicalEvidence evidence = CanonicalEvidence.builder().id(20L).organisation(org).externalId("SRC_CMMS_1").sourceType("CMMS").title("Maintenance").isDeleted(false).contentHash("h").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).build();

    @BeforeEach void setup() {
        MockitoAnnotations.openMocks(this);
        service = new InvestigationFindingService(investigationRepository, findingRepository, linkRepository, investigationEvidenceRepository, evidenceRepository, assessmentRepository, authorizationService);
        when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        when(authorizationService.getCurrentUser()).thenReturn(user);
        doNothing().when(authorizationService).requireEvidenceGraphAccess();
        when(investigationRepository.findByIdAndOrganisationOrgId(8L, 1L)).thenReturn(Optional.of(investigation));
        when(evidenceRepository.findByExternalIdAndOrganisationOrgId("SRC_CMMS_1", 1L)).thenReturn(Optional.of(evidence));
        evidence.setIncident(investigation);
        when(assessmentRepository.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(1L, 8L, 20L)).thenReturn(Optional.of(InvestigationEvidenceAssessment.builder().reviewStatus("REVIEWED").relevance("RELEVANT").assessment("SUPPORTS_INVESTIGATION").build()));
        when(findingRepository.save(any())).thenAnswer(invocation -> { var finding = invocation.getArgument(0, com.taceiq.entity.InvestigationFinding.class); finding.setId(1L); return finding; });
    }

    @Test void createsFindingUsingReviewedRelevantEvidence() {
        var request = new InvestigationFindingRequest(); request.setStatement("Maintenance finding"); request.setCategory("EQUIPMENT"); request.setConfidence("HIGH"); request.setEvidence(List.of(link("SRC_CMMS_1", "SUPPORTING")));
        var result = service.create(8L, request);
        assertEquals("Maintenance finding", result.getStatement());
        verify(linkRepository).save(any());
    }

    @Test void rejectsUnreviewedEvidence() {
        when(assessmentRepository.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(1L, 8L, 20L)).thenReturn(Optional.empty());
        var request = new InvestigationFindingRequest(); request.setStatement("Invalid"); request.setEvidence(List.of(link("SRC_CMMS_1", "SUPPORTING")));
        assertThrows(ResponseStatusException.class, () -> service.create(8L, request));
        verify(linkRepository, never()).save(any());
    }

    @Test void acceptsReviewedContradictoryEvidence() {
        when(assessmentRepository.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(1L, 8L, 20L))
                .thenReturn(Optional.of(InvestigationEvidenceAssessment.builder().reviewStatus("REVIEWED").relevance("RELEVANT").assessment("CONTRADICTS_INVESTIGATION").build()));
        var request = new InvestigationFindingRequest(); request.setStatement("Contradictory finding"); request.setEvidence(List.of(link("SRC_CMMS_1", "CONTRADICTING")));
        assertDoesNotThrow(() -> service.create(8L, request));
        verify(linkRepository).save(argThat(value -> "CONTRADICTING".equals(value.getRelationshipType())));
    }

    @Test void rejectsReviewedNotRelevantEvidence() {
        when(assessmentRepository.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(1L, 8L, 20L))
                .thenReturn(Optional.of(InvestigationEvidenceAssessment.builder().reviewStatus("REVIEWED").relevance("NOT_RELEVANT").assessment("SUPPORTS_INVESTIGATION").build()));
        var request = new InvestigationFindingRequest(); request.setStatement("Not relevant"); request.setEvidence(List.of(link("SRC_CMMS_1", "SUPPORTING")));
        assertThrows(ResponseStatusException.class, () -> service.create(8L, request));
        verify(linkRepository, never()).save(any());
    }

    @Test void rejectsPendingReviewEvidence() {
        when(assessmentRepository.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(1L, 8L, 20L))
                .thenReturn(Optional.of(InvestigationEvidenceAssessment.builder().reviewStatus("PENDING_REVIEW").relevance("RELEVANT").assessment("SUPPORTS_INVESTIGATION").build()));
        var request = new InvestigationFindingRequest(); request.setStatement("Pending"); request.setEvidence(List.of(link("SRC_CMMS_1", "SUPPORTING")));
        assertThrows(ResponseStatusException.class, () -> service.create(8L, request));
        verify(linkRepository, never()).save(any());
    }

    @Test void rejectsReviewedRelevantEvidenceWithoutAssessment() {
        when(assessmentRepository.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(1L, 8L, 20L))
                .thenReturn(Optional.of(InvestigationEvidenceAssessment.builder().reviewStatus("REVIEWED").relevance("RELEVANT").build()));
        var request = new InvestigationFindingRequest(); request.setStatement("Unassessed"); request.setEvidence(List.of(link("SRC_CMMS_1", "SUPPORTING")));
        assertThrows(ResponseStatusException.class, () -> service.create(8L, request));
        verify(linkRepository, never()).save(any());
    }

    @Test void acceptsReusedEvidenceThroughInvestigationLink() {
        evidence.setIncident(null);
        when(investigationEvidenceRepository.existsByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(1L, 8L, 20L)).thenReturn(true);
        var request = new InvestigationFindingRequest(); request.setStatement("Reused evidence finding"); request.setEvidence(List.of(link("SRC_CMMS_1", "SUPPORTING")));
        assertDoesNotThrow(() -> service.create(8L, request));
        verify(linkRepository).save(any());
    }

    @Test void rejectsMissingPermission() {
        doThrow(new AccessDeniedException("Missing")).when(authorizationService).requireEvidenceGraphAccess();
        assertThrows(AccessDeniedException.class, () -> service.list(8L));
    }

    private InvestigationFindingRequest.EvidenceLinkRequest link(String id, String type) { var link = new InvestigationFindingRequest.EvidenceLinkRequest(); link.setStableId(id); link.setRelationshipType(type); return link; }
}
