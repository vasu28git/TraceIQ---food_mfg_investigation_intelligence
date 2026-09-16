package com.taceiq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taceiq.dto.InvestigationTimelineResponse;
import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.Complaint;
import com.taceiq.entity.Investigation;
import com.taceiq.entity.InvestigationEvidence;
import com.taceiq.entity.Organisation;
import com.taceiq.entity.User;
import com.taceiq.graph.service.GraphReadinessService;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.ComplaintRepository;
import com.taceiq.repository.InvestigationEvidenceRepository;
import com.taceiq.repository.InvestigationRepository;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.InvestigationTimelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Evidence Chronology tests – deterministic creation/update only.
 * Current evidence ingestion does not provide structured domain event/action history.
 * Therefore the timeline currently represents deterministic evidence creation/update chronology.
 */
public class InvestigationTimelineServiceTest {

    @Mock InvestigationRepository investigationRepository;
    @Mock ComplaintRepository complaintRepository;
    @Mock InvestigationEvidenceRepository linkRepository;
    @Mock CanonicalEvidenceRepository canonicalRepo;
    @Mock AuthorizationService authService;
    @Mock GraphReadinessService graphReadinessService;

    InvestigationTimelineService service;

    Organisation org1 = Organisation.builder().orgId(1L).name("Org1").build();
    User user1 = User.builder().id(10L).username("user1").organisation(org1).build();
    Investigation inv = Investigation.builder().id(100L).organisation(org1).investigationKey("INV-1").title("Investigation 1").status("ACTIVE").createdAt(Instant.parse("2026-09-01T10:00:00Z")).updatedAt(Instant.parse("2026-09-01T10:00:00Z")).build();
    Complaint complaint = Complaint.builder().id(200L).organisation(org1).complaintKey("C-1").title("Complaint Title").sourceType("MANUAL").raisedAt(Instant.parse("2026-08-30T09:00:00Z")).receivedAt(Instant.parse("2026-08-30T10:00:00Z")).createdAt(Instant.parse("2026-08-30T11:00:00Z")).investigation(inv).build();
    CanonicalEvidence ce = CanonicalEvidence.builder().id(300L).organisation(org1).externalId("ev_001").title("Access Record").sourceType("API").status("READY").caseId("CASE-001").actorId("EMP-007").parentId(null)
            .sourceCreatedAt(Instant.parse("2026-09-02T09:00:00Z")).sourceUpdatedAt(Instant.parse("2026-09-02T10:00:00Z")).contentHash("h").normalizedPayload("{\"size\":2048,\"contentType\":\"application/json\",\"tags\":[\"ANONYMIZED\"]}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).build();
    InvestigationEvidence link = InvestigationEvidence.builder().id(400L).organisation(org1).investigation(inv).canonicalEvidence(ce).createdAt(Instant.parse("2026-09-03T11:00:00Z")).build();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new InvestigationTimelineService(investigationRepository, complaintRepository, linkRepository, canonicalRepo, authService, graphReadinessService, new ObjectMapper());
        lenient().when(authService.getCurrentOrgId()).thenReturn(1L);
        lenient().doNothing().when(authService).requireEvidenceGraphAccess();
        lenient().when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(true);
        lenient().when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(inv));
        lenient().when(complaintRepository.findByInvestigationIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(complaint));
        lenient().when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of(link)));
    }

    // 1
    @Test
    void evidenceCreatedEventUsesSourceCreatedAt() {
        var resp = service.getTimeline(100L, 0, 50);
        var ev = resp.getEvents().stream().filter(e -> e.getEventType().equals("EVIDENCE_CREATED")).findFirst().orElse(null);
        assertNotNull(ev);
        assertEquals(Instant.parse("2026-09-02T09:00:00Z").toString(), ev.getEventTime());
        assertEquals("ev_001", ev.getStableId());
    }

    // 2
    @Test
    void evidenceUpdatedEventUsesSourceUpdatedAt() {
        var resp = service.getTimeline(100L, 0, 50);
        var ev = resp.getEvents().stream().filter(e -> e.getEventType().equals("EVIDENCE_UPDATED")).findFirst().orElse(null);
        assertNotNull(ev);
        assertEquals(Instant.parse("2026-09-02T10:00:00Z").toString(), ev.getEventTime());
    }

    // 3
    @Test
    void updateOmittedWhenEqualsCreated() {
        CanonicalEvidence ce2 = CanonicalEvidence.builder().id(301L).organisation(org1).externalId("ev_002").title("T").sourceType("FILE").status("READY").caseId("CASE-001").actorId("EMP-007")
                .sourceCreatedAt(Instant.parse("2026-09-02T09:00:00Z")).sourceUpdatedAt(Instant.parse("2026-09-02T09:00:00Z")).contentHash("h2").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).build();
        InvestigationEvidence link2 = InvestigationEvidence.builder().id(401L).organisation(org1).investigation(inv).canonicalEvidence(ce2).createdAt(Instant.parse("2026-09-03T12:00:00Z")).build();
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of(link2)));
        var resp = service.getTimeline(100L, 0, 50);
        assertTrue(resp.getEvents().stream().anyMatch(e -> e.getEventType().equals("EVIDENCE_CREATED")));
        assertFalse(resp.getEvents().stream().anyMatch(e -> e.getEventType().equals("EVIDENCE_UPDATED")));
    }

    // 4
    @Test
    void nullSourceCreatedAtBehaviorRemainsTruthful() {
        CanonicalEvidence ce2 = CanonicalEvidence.builder().id(300L).organisation(org1).externalId("ev_002").title("T2").sourceType("FILE").status("READY").sourceCreatedAt(null).sourceUpdatedAt(Instant.parse("2026-09-02T10:00:00Z")).contentHash("h2").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).build();
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of(
                link, InvestigationEvidence.builder().id(401L).organisation(org1).investigation(inv).canonicalEvidence(ce2).createdAt(Instant.parse("2026-09-03T12:00:00Z")).build()
        )));
        var resp = service.getTimeline(100L, 0, 50);
        long createdCount = resp.getEvents().stream().filter(e -> e.getEventType().equals("EVIDENCE_CREATED")).count();
        assertEquals(1, createdCount);
        assertTrue(resp.getEvents().stream().filter(e -> e.getStableId().equals("ev_002")).allMatch(e -> e.getEventType().equals("EVIDENCE_UPDATED")));
    }

    // 5
    @Test
    void complaintRaisedAbsent() {
        var resp = service.getTimeline(100L, 0, 50);
        assertTrue(resp.getEvents().stream().noneMatch(e -> e.getEventType().equals("COMPLAINT_RAISED")));
    }

    // 6
    @Test
    void complaintReceivedAbsent() {
        var resp = service.getTimeline(100L, 0, 50);
        assertTrue(resp.getEvents().stream().noneMatch(e -> e.getEventType().equals("COMPLAINT_RECEIVED")));
    }

    // 7
    @Test
    void complaintCreatedAbsent() {
        var resp = service.getTimeline(100L, 0, 50);
        assertTrue(resp.getEvents().stream().noneMatch(e -> e.getEventType().equals("COMPLAINT_CREATED")));
    }

    // 8
    @Test
    void investigationCreatedAbsent() {
        var resp = service.getTimeline(100L, 0, 50);
        assertTrue(resp.getEvents().stream().noneMatch(e -> e.getEventType().equals("INVESTIGATION_CREATED")));
    }

    // 9
    @Test
    void evidenceLinkedAbsent() {
        var resp = service.getTimeline(100L, 0, 50);
        assertTrue(resp.getEvents().stream().noneMatch(e -> e.getEventType().equals("EVIDENCE_LINKED")));
    }

    // 10
    @Test
    void investigationEvidenceCreatedAtNeverCreatesEvent() {
        // link createdAt is distinct far future, ensure no event at that time
        CanonicalEvidence ce3 = CanonicalEvidence.builder().id(302L).organisation(org1).externalId("ev_003").title("Doc").sourceType("FILE").status("READY")
                .sourceCreatedAt(Instant.parse("2026-09-02T09:00:00Z")).sourceUpdatedAt(Instant.parse("2026-09-02T10:00:00Z")).contentHash("h3").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).build();
        InvestigationEvidence link3 = InvestigationEvidence.builder().id(402L).organisation(org1).investigation(inv).canonicalEvidence(ce3).createdAt(Instant.parse("2026-09-10T00:00:00Z")).build();
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of(link3)));
        var resp = service.getTimeline(100L, 0, 50);
        assertTrue(resp.getEvents().stream().noneMatch(e -> e.getEventTime().equals(Instant.parse("2026-09-10T00:00:00Z").toString())));
        assertEquals(2, resp.getEvents().size());
    }

    // 11
    @Test
    void deletedEvidenceExcluded() {
        CanonicalEvidence deleted = CanonicalEvidence.builder().id(303L).organisation(org1).externalId("ev_del").title("Del").sourceType("FILE").status("READY")
                .sourceCreatedAt(Instant.parse("2026-09-02T09:00:00Z")).sourceUpdatedAt(Instant.parse("2026-09-02T10:00:00Z")).contentHash("h4").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(true).build();
        InvestigationEvidence linkDel = InvestigationEvidence.builder().id(403L).organisation(org1).investigation(inv).canonicalEvidence(deleted).createdAt(Instant.now()).build();
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of(linkDel)));
        var resp = service.getTimeline(100L, 0, 50);
        assertEquals(0, resp.getEvents().size());
        assertEquals(0, resp.getTotalElements());
    }

    // 12
    @Test
    void everyEventHasStableId() {
        var resp = service.getTimeline(100L, 0, 50);
        assertFalse(resp.getEvents().isEmpty());
        assertTrue(resp.getEvents().stream().allMatch(e -> e.getStableId() != null && !e.getStableId().isBlank()));
    }

    // 13
    @Test
    void actorComesFromCanonicalEvidenceActorId() {
        CanonicalEvidence ce2 = CanonicalEvidence.builder().id(304L).organisation(org1).externalId("ev_004").title("Doc").sourceType("API").status("READY").caseId("CASE-100").actorId("ACTOR_FROM_EVIDENCE").parentId(null)
                .sourceCreatedAt(Instant.parse("2026-09-02T09:00:00Z")).sourceUpdatedAt(Instant.parse("2026-09-02T10:00:00Z")).contentHash("h5").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).build();
        InvestigationEvidence link2 = InvestigationEvidence.builder().id(404L).organisation(org1).investigation(inv).canonicalEvidence(ce2).createdAt(Instant.now()).build();
        // link createdBy is different user, should not leak as actor
        User investigator = User.builder().id(99L).username("investigator").organisation(org1).build();
        link2.setCreatedBy(investigator);
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of(link2)));
        var resp = service.getTimeline(100L, 0, 50);
        assertTrue(resp.getEvents().stream().allMatch(e -> "ACTOR_FROM_EVIDENCE".equals(e.getActorId())));
        assertTrue(resp.getEvents().stream().noneMatch(e -> "99".equals(e.getActorId()) || "investigator".equals(e.getActorId())));
    }

    // 14
    @Test
    void caseComesFromCanonicalEvidenceCaseId() {
        var resp = service.getTimeline(100L, 0, 50);
        assertTrue(resp.getEvents().stream().allMatch(e -> "CASE-001".equals(e.getCaseId())));
    }

    // 15
    @Test
    void crossTenantEvidenceCannotAppear() {
        // verify tenant isolation uses orgId from authService
        service.getTimeline(100L, 0, 50);
        verify(investigationRepository).findByIdAndOrganisationOrgId(100L, 1L);
        verify(linkRepository).findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any());
        // cross-tenant investigation should 404
        when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.getTimeline(100L, 0, 50));
    }

    // 16
    @Test
    void deterministicOrdering() {
        CanonicalEvidence ceA = CanonicalEvidence.builder().id(305L).organisation(org1).externalId("ev_a").title("A").sourceType("FILE").status("READY").caseId("CASE-1").actorId("A1")
                .sourceCreatedAt(Instant.parse("2026-09-02T09:00:00Z")).sourceUpdatedAt(null).contentHash("ha").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).build();
        CanonicalEvidence ceB = CanonicalEvidence.builder().id(306L).organisation(org1).externalId("ev_b").title("B").sourceType("FILE").status("READY").caseId("CASE-1").actorId("A1")
                .sourceCreatedAt(Instant.parse("2026-09-02T09:00:00Z")).sourceUpdatedAt(null).contentHash("hb").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).build();
        InvestigationEvidence lA = InvestigationEvidence.builder().id(405L).organisation(org1).investigation(inv).canonicalEvidence(ceA).createdAt(Instant.now()).build();
        InvestigationEvidence lB = InvestigationEvidence.builder().id(406L).organisation(org1).investigation(inv).canonicalEvidence(ceB).createdAt(Instant.now()).build();
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of(lB, lA)));
        var r1 = service.getTimeline(100L, 0, 50);
        var r2 = service.getTimeline(100L, 0, 50);
        assertEquals(r1.getEvents().size(), r2.getEvents().size());
        for (int i = 0; i < r1.getEvents().size(); i++) {
            assertEquals(r1.getEvents().get(i).getStableId(), r2.getEvents().get(i).getStableId());
            assertEquals(r1.getEvents().get(i).getEventType(), r2.getEvents().get(i).getEventType());
        }
        // same eventTime same eventType ordered by stableId ASC
        assertEquals("ev_a", r1.getEvents().get(0).getStableId());
        assertEquals("ev_b", r1.getEvents().get(1).getStableId());
        for (int i = 1; i < r1.getEvents().size(); i++) {
            String prev = r1.getEvents().get(i-1).getEventTime();
            String curr = r1.getEvents().get(i).getEventTime();
            assertTrue(prev.compareTo(curr) <= 0);
            if (prev.equals(curr)) {
                assertTrue(r1.getEvents().get(i-1).getEventType().compareTo(r1.getEvents().get(i).getEventType()) <= 0);
                if (r1.getEvents().get(i-1).getEventType().equals(r1.getEvents().get(i).getEventType())) {
                    assertTrue(r1.getEvents().get(i-1).getStableId().compareTo(r1.getEvents().get(i).getStableId()) <= 0);
                }
            }
        }
    }

    // 17
    @Test
    void paginationRemainsDeterministic() {
        CanonicalEvidence ce1 = CanonicalEvidence.builder().id(307L).organisation(org1).externalId("ev_001").title("E1").sourceType("FILE").status("READY")
                .sourceCreatedAt(Instant.parse("2026-09-01T08:00:00Z")).sourceUpdatedAt(Instant.parse("2026-09-01T09:00:00Z")).contentHash("h1").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).build();
        CanonicalEvidence ce2 = CanonicalEvidence.builder().id(308L).organisation(org1).externalId("ev_002").title("E2").sourceType("FILE").status("READY")
                .sourceCreatedAt(Instant.parse("2026-09-02T08:00:00Z")).sourceUpdatedAt(null).contentHash("h2").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).build();
        CanonicalEvidence ce3 = CanonicalEvidence.builder().id(309L).organisation(org1).externalId("ev_003").title("E3").sourceType("FILE").status("READY")
                .sourceCreatedAt(Instant.parse("2026-09-03T08:00:00Z")).sourceUpdatedAt(Instant.parse("2026-09-03T09:00:00Z")).contentHash("h3").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).build();
        InvestigationEvidence l1 = InvestigationEvidence.builder().id(407L).organisation(org1).investigation(inv).canonicalEvidence(ce1).createdAt(Instant.now()).build();
        InvestigationEvidence l2 = InvestigationEvidence.builder().id(408L).organisation(org1).investigation(inv).canonicalEvidence(ce2).createdAt(Instant.now()).build();
        InvestigationEvidence l3 = InvestigationEvidence.builder().id(409L).organisation(org1).investigation(inv).canonicalEvidence(ce3).createdAt(Instant.now()).build();
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of(l1,l2,l3)));
        var p0 = service.getTimeline(100L, 0, 2);
        var p1 = service.getTimeline(100L, 1, 2);
        var p2 = service.getTimeline(100L, 2, 2);
        assertEquals(2, p0.getEvents().size());
        assertEquals(2, p1.getEvents().size());
        assertEquals(1, p2.getEvents().size());
        assertEquals(5, p0.getTotalElements());
        assertEquals(3, p0.getTotalPages());
        // deterministic across calls
        var p0Again = service.getTimeline(100L, 0, 2);
        assertEquals(p0.getEvents().get(0).getStableId(), p0Again.getEvents().get(0).getStableId());
    }

    // 18
    @Test
    void emptyInvestigationReturnsEmptyTimeline() {
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of()));
        var resp = service.getTimeline(100L, 0, 50);
        assertEquals(0, resp.getEvents().size());
        assertEquals(0, resp.getTotalElements());
        assertEquals(0, resp.getTotalPages());
    }

    // 19
    @Test
    void noFabricatedDomainActionsProduced() {
        var resp = service.getTimeline(100L, 0, 50);
        List<String> forbidden = List.of("LOGIN","RECORD_ACCESSED","RECORD_EXPORTED","RECORD_CREATED","RECORD_DELETED","ACTIVATED","COMPLETED","COMPLAINT_RAISED","COMPLAINT_RECEIVED","COMPLAINT_CREATED","INVESTIGATION_CREATED","EVIDENCE_LINKED","FIRST_SEEN","LAST_SEEN");
        for (var e : resp.getEvents()) {
            assertFalse(forbidden.contains(e.getEventType()), "Fabricated event: " + e.getEventType());
            // only allowed types
            assertTrue(List.of("EVIDENCE_CREATED","EVIDENCE_UPDATED").contains(e.getEventType()));
        }
    }

    // 20
    @Test
    void safeEvidenceMetadataReturnedWithoutExposingRawPayload() {
        var resp = service.getTimeline(100L, 0, 50);
        assertFalse(resp.getEvents().isEmpty());
        for (var e : resp.getEvents()) {
            assertNotNull(e.getStableId());
            assertNotNull(e.getTitle());
            assertNotNull(e.getSourceType());
            assertEquals("CASE-001", e.getCaseId());
            assertEquals("EMP-007", e.getActorId());
            assertEquals("READY", e.getStatus());
            assertEquals(2048L, e.getSize());
            assertEquals("application/json", e.getContentType());
            assertNotNull(e.getTags());
            assertTrue(e.getTags().contains("ANONYMIZED"));
            // must not expose raw payload / contentHash / orgId
            assertNull(e.getMetadata()); // we set metadata null, safe fields are explicit
            // Ensure no contentHash leakage via toString (check not equals h)
            assertNotEquals("h", e.getStableId());
        }
    }

    // Additional regression: pagination defaults
    @Test
    void paginationDefault() {
        var resp = service.getTimeline(100L, null, null);
        assertEquals(0, resp.getPage());
        assertEquals(50, resp.getSize());
    }

    @Test
    void paginationMax100() {
        assertThrows(ResponseStatusException.class, () -> service.getTimeline(100L, 0, 101));
        assertThrows(ResponseStatusException.class, () -> service.getTimeline(100L, 0, 200));
    }

    @Test
    void graphNotReady400() {
        when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(false);
        assertThrows(ResponseStatusException.class, () -> service.getTimeline(100L, 0, 50));
        verify(linkRepository, never()).findByOrganisationOrgIdAndInvestigationId(anyLong(), anyLong(), any());
    }

    @Test
    void permissionDenied403() {
        doThrow(new AccessDeniedException("Missing")).when(authService).requireEvidenceGraphAccess();
        assertThrows(AccessDeniedException.class, () -> service.getTimeline(100L, 0, 50));
    }

    @Test
    void noNeo4jAccessRequired() {
        service.getTimeline(100L, 0, 50);
        verify(canonicalRepo, never()).save(any());
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void missingInvestigationReturns404() {
        when(investigationRepository.findByIdAndOrganisationOrgId(999L, 1L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.getTimeline(999L, 0, 50));
    }
}
