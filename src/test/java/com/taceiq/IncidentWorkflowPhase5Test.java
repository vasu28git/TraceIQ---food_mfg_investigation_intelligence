package com.taceiq;

import com.taceiq.dto.*;
import com.taceiq.entity.*;
import com.taceiq.graph.service.GraphReadinessService;
import com.taceiq.repository.*;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
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

public class IncidentWorkflowPhase5Test {

    @Mock InvestigationRepository investigationRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock AuthorizationService authorizationService;
    @Mock GraphReadinessService graphReadinessService;
    @Mock CanonicalEvidenceRepository canonicalRepo;
    @Mock InvestigationEvidenceRepository linkRepository;
    @Mock InvestigationRepository invRepo2;
    @Mock ComplaintRepository complaintRepository;

    InvestigationService investigationService;
    InvestigationEvidenceService evidenceService;
    InvestigationTimelineService timelineService;

    Organisation orgA = Organisation.builder().orgId(1L).name("OrgA").build();
    Organisation orgB = Organisation.builder().orgId(2L).name("OrgB").build();
    User userA = User.builder().id(10L).username("userA").organisation(orgA).build();
    Investigation incidentA = Investigation.builder().id(100L).organisation(orgA).investigationKey("INC-001").title("Incident A").status("DRAFT").createdBy(userA).build();
    Investigation incidentB = Investigation.builder().id(200L).organisation(orgB).investigationKey("INC-002").title("Incident B").status("DRAFT").createdBy(User.builder().id(20L).organisation(orgB).build()).build();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        investigationService = new InvestigationService(investigationRepository, organisationRepository, authorizationService, graphReadinessService);
        evidenceService = new InvestigationEvidenceService(invRepo2, canonicalRepo, linkRepository, authorizationService, graphReadinessService);
        // timeline needs mapper
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        timelineService = new InvestigationTimelineService(invRepo2, complaintRepository, linkRepository, canonicalRepo, authorizationService, graphReadinessService, mapper);

        lenient().when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        lenient().when(authorizationService.getCurrentUser()).thenReturn(userA);
        lenient().doNothing().when(authorizationService).requireEvidenceGraphAccess();
        lenient().when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(true);
        lenient().when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        lenient().when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(incidentA));
        lenient().when(invRepo2.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(incidentA));
        lenient().when(invRepo2.findByIdAndOrganisationOrgId(200L, 2L)).thenReturn(Optional.of(incidentB));
        lenient().when(investigationRepository.save(any())).thenAnswer(i -> { Object arg = i.getArgument(0); if (arg == null) return null; Investigation inv = (Investigation) arg; if (inv.getId() == null) inv.setId(100L); if (inv.getCreatedAt() == null) inv.setCreatedAt(Instant.now()); inv.setUpdatedAt(Instant.now()); return inv; });
        lenient().when(investigationRepository.existsByInvestigationKeyAndOrganisationOrgId(anyString(), eq(1L))).thenReturn(false);
    }

    // 1. incident evidence list — direct canonical.incident_id discoverable
    @Test
    void evidenceList_includesDirectIncidentEvidence() {
        CanonicalEvidence direct = CanonicalEvidence.builder().id(500L).organisation(orgA).incident(incidentA).externalId("ev_direct").title("direct").sourceType("FILE").status("READY").contentHash("h").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).build();
        when(canonicalRepo.findByIncidentIdAndOrganisationOrgId(100L, 1L)).thenReturn(List.of(direct));
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of()));
        Page<InvestigationEvidenceResponse> page = evidenceService.listEvidence(100L, 0, 20);
        assertEquals(1, page.getTotalElements());
        assertEquals("ev_direct", page.getContent().get(0).getStableId());
    }

    // 2. incident evidence tenant isolation — orgB evidence not leaked
    @Test
    void evidenceList_tenantIsolation() {
        CanonicalEvidence otherOrg = CanonicalEvidence.builder().id(501L).organisation(orgB).incident(incidentB).externalId("ev_other").contentHash("h").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).build();
        when(canonicalRepo.findByIncidentIdAndOrganisationOrgId(100L, 1L)).thenReturn(List.of());
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of()));
        // Try to list incident 100 as org 1 — should not see orgB evidence
        Page<InvestigationEvidenceResponse> page = evidenceService.listEvidence(100L, 0, 20);
        assertEquals(0, page.getTotalElements());
        verify(canonicalRepo).findByIncidentIdAndOrganisationOrgId(100L, 1L);
        verify(canonicalRepo, never()).findByIncidentIdAndOrganisationOrgId(100L, 2L);
    }

    // 3. incident evidence linking — valid
    @Test
    void evidenceLinking_valid() {
        CanonicalEvidence ev = CanonicalEvidence.builder().id(600L).organisation(orgA).externalId("ev_001").title("t").sourceType("FILE").status("READY").contentHash("h").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).build();
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("ev_001", 1L)).thenReturn(Optional.of(ev));
        when(linkRepository.existsByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(1L, 100L, 600L)).thenReturn(false);
        when(linkRepository.save(any())).thenAnswer(i -> { var l = (InvestigationEvidence) i.getArgument(0); l.setId(700L); return l; });
        var resp = evidenceService.linkEvidence(100L, "ev_001");
        assertEquals("ev_001", resp.getStableId());
    }

    // 4. duplicate evidence link is idempotent — 409
    @Test
    void duplicateEvidenceLink_isIdempotent_409() {
        CanonicalEvidence ev = CanonicalEvidence.builder().id(600L).organisation(orgA).externalId("ev_001").isDeleted(false).contentHash("h").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).build();
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("ev_001", 1L)).thenReturn(Optional.of(ev));
        when(linkRepository.existsByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(1L, 100L, 600L)).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> evidenceService.linkEvidence(100L, "ev_001"));
    }

    // 5. cross-tenant evidence cannot be linked — 404
    @Test
    void crossTenantEvidenceCannotBeLinked() {
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("ev_other", 1L)).thenReturn(Optional.empty());
        var ex = assertThrows(ResponseStatusException.class, () -> evidenceService.linkEvidence(100L, "ev_other"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // 6. timeline incident scoping — includes direct incident evidence
    @Test
    void timeline_incidentScoping_includesDirectEvidence() {
        Investigation inv = incidentA;
        CanonicalEvidence ce = CanonicalEvidence.builder().id(800L).organisation(orgA).incident(inv).externalId("ev_timeline").title("t").sourceType("FILE").status("READY").contentHash("h").normalizedPayload("{}")
                .sourceCreatedAt(Instant.parse("2026-09-01T08:00:00Z")).sourceUpdatedAt(Instant.parse("2026-09-03T10:00:00Z"))
                .firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).build();
        when(canonicalRepo.findByIncidentIdAndOrganisationOrgId(100L, 1L)).thenReturn(List.of(ce));
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of()));
        var resp = timelineService.getTimeline(100L, 0, 50);
        assertTrue(resp.getTotalElements() >= 1);
        assertTrue(resp.getEvents().stream().anyMatch(e -> "ev_timeline".equals(e.getStableId())));
    }

    @Test
    void timeline_wrongTenant_404() {
        when(invRepo2.findByIdAndOrganisationOrgId(200L, 1L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> timelineService.getTimeline(200L, 0, 50));
    }

    // 9. completion — ACTIVE → COMPLETED
    @Test
    void completion_transitionActiveToCompleted() {
        Investigation invActive = Investigation.builder().id(100L).organisation(orgA).investigationKey("INC-001").title("t").status("ACTIVE").createdBy(userA).build();
        when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(invActive));
        when(investigationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var resp = investigationService.complete(100L);
        assertEquals("COMPLETED", resp.getStatus());
    }

    // 10. archive behavior — COMPLETED → ARCHIVED
    @Test
    void archive_behavior() {
        Investigation invCompleted = Investigation.builder().id(100L).organisation(orgA).investigationKey("INC-001").title("t").status("COMPLETED").createdBy(userA).build();
        when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(invCompleted));
        when(investigationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var resp = investigationService.archive(100L);
        assertEquals("ARCHIVED", resp.getStatus());
    }

    // 11. Incident can exist with zero evidence — list returns empty, not error
    @Test
    void incidentCanExistWithZeroEvidence() {
        when(canonicalRepo.findByIncidentIdAndOrganisationOrgId(100L, 1L)).thenReturn(List.of());
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of()));
        var page = evidenceService.listEvidence(100L, 0, 20);
        assertEquals(0, page.getTotalElements());
        // Timeline also empty
        var timeline = timelineService.getTimeline(100L, 0, 50);
        assertEquals(0, timeline.getTotalElements());
    }

    // 12. Incident creation does not require Neo4j readiness
    @Test
    void incidentCreationDoesNotRequireNeo4j() {
        when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(false);
        // Should still succeed
        CreateInvestigationRequest req = CreateInvestigationRequest.builder().investigationKey("INC-NEO4J").title("Incident without Neo4j").build();
        var resp = investigationService.create(req);
        assertEquals("DRAFT", resp.getStatus());
        verify(graphReadinessService, never()).isOrgGraphReady(anyLong());
    }

    // 13. Complaint remains optional — create without complaint still works
    @Test
    void complaintRemainsOptional() {
        // investigationService.create does not require complaint — already verified by 12
        // Also verify that investigation without complaint can be activated
        Investigation invDraft = Investigation.builder().id(100L).organisation(orgA).investigationKey("INC-001").title("t").status("DRAFT").createdBy(userA).build();
        when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(invDraft));
        when(investigationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var resp = investigationService.activate(100L);
        assertEquals("ACTIVE", resp.getStatus());
        // No complaint repository interaction needed
        verifyNoInteractions(complaintRepository);
    }

    // 14. legacy investigation/case APIs still work — evidence via join table
    @Test
    void legacyInvestigationEvidenceStillWorks() {
        CanonicalEvidence ev = CanonicalEvidence.builder().id(900L).organisation(orgA).externalId("ev_legacy").title("legacy").sourceType("FILE").status("READY").contentHash("h").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).build();
        var link = InvestigationEvidence.builder().id(1L).organisation(orgA).investigation(incidentA).canonicalEvidence(ev).createdAt(Instant.now()).build();
        when(canonicalRepo.findByIncidentIdAndOrganisationOrgId(100L, 1L)).thenReturn(List.of());
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of(link)));
        var page = evidenceService.listEvidence(100L, 0, 20);
        assertEquals(1, page.getTotalElements());
        assertEquals("ev_legacy", page.getContent().get(0).getStableId());
    }

    // Additional: union deduplication — direct + linked same canonical not duplicated
    @Test
    void evidenceUnion_deduplicates() {
        CanonicalEvidence same = CanonicalEvidence.builder().id(1000L).organisation(orgA).incident(incidentA).externalId("ev_same").title("same").sourceType("FILE").status("READY").contentHash("h").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).build();
        var link = InvestigationEvidence.builder().id(1L).organisation(orgA).investigation(incidentA).canonicalEvidence(same).createdAt(Instant.now()).build();
        when(canonicalRepo.findByIncidentIdAndOrganisationOrgId(100L, 1L)).thenReturn(List.of(same));
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of(link)));
        var page = evidenceService.listEvidence(100L, 0, 20);
        assertEquals(1, page.getTotalElements(), "duplicate canonical should be deduplicated");
    }
}
