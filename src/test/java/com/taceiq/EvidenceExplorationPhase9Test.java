package com.taceiq;

import com.taceiq.dto.InvestigationEvidenceResponse;
import com.taceiq.entity.*;
import com.taceiq.graph.service.GraphReadinessService;
import com.taceiq.repository.*;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.InvestigationEvidenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class EvidenceExplorationPhase9Test {

    @Mock InvestigationRepository investigationRepository;
    @Mock CanonicalEvidenceRepository canonicalRepo;
    @Mock InvestigationEvidenceRepository linkRepository;
    @Mock AuthorizationService authService;
    @Mock GraphReadinessService graphReadinessService;

    InvestigationEvidenceService evidenceService;

    Organisation orgA = Organisation.builder().orgId(1L).name("OrgA").build();
    Organisation orgB = Organisation.builder().orgId(2L).name("OrgB").build();
    User userA = User.builder().id(10L).username("userA").organisation(orgA).build();
    Investigation incidentA = Investigation.builder().id(100L).organisation(orgA).investigationKey("INC-001").title("Incident A").status("ACTIVE").createdBy(userA).createdAt(Instant.now()).updatedAt(Instant.now()).build();
    Investigation incidentB = Investigation.builder().id(200L).organisation(orgB).investigationKey("INC-002").title("Incident B").status("ACTIVE").createdAt(Instant.now()).updatedAt(Instant.now()).build();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        evidenceService = new InvestigationEvidenceService(investigationRepository, canonicalRepo, linkRepository, authService, graphReadinessService);
        lenient().when(authService.getCurrentOrgId()).thenReturn(1L);
        lenient().when(authService.getCurrentUser()).thenReturn(userA);
        lenient().doNothing().when(authService).requireEvidenceGraphAccess();
        lenient().when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(true);
        lenient().when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(incidentA));
        lenient().when(investigationRepository.findByIdAndOrganisationOrgId(200L, 1L)).thenReturn(Optional.empty());
    }

    CanonicalEvidence ce(String stableId, String title, String sourceType, String status, Investigation incident, Organisation org) {
        return CanonicalEvidence.builder().id((long) stableId.hashCode()).organisation(org).incident(incident).externalId(stableId).title(title).sourceType(sourceType).status(status).contentHash("h").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).createdAt(Instant.now()).build();
    }

    // 1. Evidence list remains incident-scoped
    @Test
    void evidenceListRemainsIncidentScoped() {
        CanonicalEvidence directA = ce("ev_incA_1", "Title A", "FILE", "READY", incidentA, orgA);
        CanonicalEvidence directB = ce("ev_incB_1", "Title B", "API", "READY", incidentB, orgB);
        when(canonicalRepo.findByIncidentIdAndOrganisationOrgId(100L, 1L)).thenReturn(List.of(directA));
        when(canonicalRepo.findByIncidentIdAndOrganisationOrgId(200L, 1L)).thenReturn(List.of(directB)); // should not be returned for incidentA
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of()));
        var page = evidenceService.listEvidence(100L, 0, 20);
        assertEquals(1, page.getTotalElements());
        assertEquals("ev_incA_1", page.getContent().get(0).getStableId());
    }

    // 2. Evidence search is incident-scoped
    @Test
    void evidenceSearchIsIncidentScoped() {
        CanonicalEvidence ev1 = ce("ev_alpha", "Alpha Document", "FILE", "READY", incidentA, orgA);
        CanonicalEvidence ev2 = ce("ev_beta", "Beta Report", "API", "READY", incidentA, orgA);
        when(canonicalRepo.findByIncidentIdAndOrganisationOrgId(100L, 1L)).thenReturn(List.of(ev1, ev2));
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of()));
        var page = evidenceService.listEvidence(100L, 0, 20, "alpha", null, null, null);
        assertEquals(1, page.getTotalElements());
        assertEquals("ev_alpha", page.getContent().get(0).getStableId());
    }

    // 3. Source filter works
    @Test
    void sourceFilterWorks() {
        CanonicalEvidence evFile = ce("ev1", "t", "FILE", "READY", incidentA, orgA);
        CanonicalEvidence evApi = ce("ev2", "t", "API", "READY", incidentA, orgA);
        when(canonicalRepo.findByIncidentIdAndOrganisationOrgId(100L, 1L)).thenReturn(List.of(evFile, evApi));
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of()));
        var page = evidenceService.listEvidence(100L, 0, 20, null, "FILE", null, null);
        assertEquals(1, page.getTotalElements());
        assertEquals("FILE", page.getContent().get(0).getSourceType());
    }

    // 4. Status filter works
    @Test
    void statusFilterWorks() {
        CanonicalEvidence evReady = ce("ev1", "t", "FILE", "READY", incidentA, orgA);
        CanonicalEvidence evPending = ce("ev2", "t", "FILE", "PENDING", incidentA, orgA);
        when(canonicalRepo.findByIncidentIdAndOrganisationOrgId(100L, 1L)).thenReturn(List.of(evReady, evPending));
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of()));
        var page = evidenceService.listEvidence(100L, 0, 20, null, null, "READY", null);
        assertEquals(1, page.getTotalElements());
        assertEquals("READY", page.getContent().get(0).getStatus());
    }

    // 5. Sorting is deterministic
    @Test
    void sortingIsDeterministic() {
        CanonicalEvidence evB = ce("ev_b", "B Title", "FILE", "READY", incidentA, orgA);
        CanonicalEvidence evA = ce("ev_a", "A Title", "FILE", "READY", incidentA, orgA);
        when(canonicalRepo.findByIncidentIdAndOrganisationOrgId(100L, 1L)).thenReturn(List.of(evB, evA));
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of()));
        var pageAsc = evidenceService.listEvidence(100L, 0, 20, null, null, null, "title,asc");
        assertEquals("ev_a", pageAsc.getContent().get(0).getStableId());
        var pageDesc = evidenceService.listEvidence(100L, 0, 20, null, null, null, "title,desc");
        assertEquals("ev_b", pageDesc.getContent().get(0).getStableId());
    }

    // 6. Pagination works
    @Test
    void paginationWorks() {
        CanonicalEvidence ev1 = ce("ev1", "t", "FILE", "READY", incidentA, orgA);
        CanonicalEvidence ev2 = ce("ev2", "t", "FILE", "READY", incidentA, orgA);
        CanonicalEvidence ev3 = ce("ev3", "t", "FILE", "READY", incidentA, orgA);
        when(canonicalRepo.findByIncidentIdAndOrganisationOrgId(100L, 1L)).thenReturn(List.of(ev1, ev2, ev3));
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of()));
        var p0 = evidenceService.listEvidence(100L, 0, 2, null, null, null, null);
        var p1 = evidenceService.listEvidence(100L, 1, 2, null, null, null, null);
        assertEquals(2, p0.getContent().size());
        assertEquals(1, p1.getContent().size());
        assertEquals(3, p0.getTotalElements());
        assertEquals(2, p0.getTotalPages());
    }

    // 7. Cross-tenant evidence cannot appear
    @Test
    void crossTenantEvidenceCannotAppear() {
        CanonicalEvidence otherOrg = ce("ev_other", "t", "FILE", "READY", incidentB, orgB);
        when(canonicalRepo.findByIncidentIdAndOrganisationOrgId(100L, 1L)).thenReturn(List.of(otherOrg));
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of()));
        var page = evidenceService.listEvidence(100L, 0, 20);
        // Other org evidence should be filtered out
        assertEquals(0, page.getTotalElements());
    }

    // 8. Cross-incident evidence cannot appear
    @Test
    void crossIncidentEvidenceCannotAppear() {
        CanonicalEvidence evOtherIncident = ce("ev_other_inc", "t", "FILE", "READY", incidentB, orgA); // same org, different incident
        // But our query is for incident 100, so it should not return ev for incident 200
        when(canonicalRepo.findByIncidentIdAndOrganisationOrgId(100L, 1L)).thenReturn(List.of());
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of()));
        var page = evidenceService.listEvidence(100L, 0, 20);
        assertEquals(0, page.getTotalElements());
        verify(canonicalRepo).findByIncidentIdAndOrganisationOrgId(100L, 1L);
        verify(canonicalRepo, never()).findByIncidentIdAndOrganisationOrgId(200L, 1L);
    }



    // 11. Existing manual upload still works (verified via incident evidence list includes manual)
    @Test
    void existingManualUploadStillWorks() {
        CanonicalEvidence manual = ce("MANUAL_FILE_1", "manual.pdf", "MANUAL_UPLOAD", "READY", incidentA, orgA);
        when(canonicalRepo.findByIncidentIdAndOrganisationOrgId(100L, 1L)).thenReturn(List.of(manual));
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of()));
        var page = evidenceService.listEvidence(100L, 0, 20);
        assertEquals(1, page.getTotalElements());
        assertEquals("MANUAL_FILE_1", page.getContent().get(0).getStableId());
    }

    // 12. Existing integration sync still works (same as manual, via canonical)
    @Test
    void existingIntegrationSyncStillWorks() {
        CanonicalEvidence apiEv = ce("ev_api_1", "t", "API", "READY", incidentA, orgA);
        when(canonicalRepo.findByIncidentIdAndOrganisationOrgId(100L, 1L)).thenReturn(List.of(apiEv));
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of()));
        var page = evidenceService.listEvidence(100L, 0, 20, null, "API", null, null);
        assertEquals(1, page.getTotalElements());
    }

    // 13. Legacy evidence endpoints still work — listEvidence without search still returns union
    @Test
    void legacyEvidenceEndpointsStillWork() {
        CanonicalEvidence legacy = ce("ev_legacy", "Legacy", "FILE", "READY", null, orgA); // incident null, but linked via join
        InvestigationEvidence link = InvestigationEvidence.builder().id(1L).organisation(orgA).investigation(incidentA).canonicalEvidence(legacy).createdAt(Instant.now()).build();
        when(canonicalRepo.findByIncidentIdAndOrganisationOrgId(100L, 1L)).thenReturn(List.of());
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of(link)));
        var page = evidenceService.listEvidence(100L, 0, 20);
        assertEquals(1, page.getTotalElements());
        assertEquals("ev_legacy", page.getContent().get(0).getStableId());
    }
}
