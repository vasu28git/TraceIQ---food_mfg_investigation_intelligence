package com.taceiq;

import com.taceiq.dto.InvestigationReportResponse;
import com.taceiq.entity.*;
import com.taceiq.graph.service.GraphReadinessService;
import com.taceiq.repository.*;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.InvestigationReportService;
import com.taceiq.service.InvestigationTimelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class InvestigationReportServiceTest {

    @Mock InvestigationRepository investigationRepository;
    @Mock ComplaintRepository complaintRepository;
    @Mock InvestigationEvidenceRepository evidenceLinkRepository;
    @Mock CanonicalEvidenceRepository canonicalRepo;
    @Mock InvestigationTimelineService timelineService;
    @Mock GraphReadinessService graphReadinessService;
    @Mock AuthorizationService authService;
    @Mock ConfigurationRepository configurationRepository;
    @Mock ConfigurationDefinitionRepository definitionRepository;

    InvestigationReportService service;

    Organisation org1 = Organisation.builder().orgId(1L).name("Org1").build();
    Organisation org2 = Organisation.builder().orgId(2L).name("Org2").build();
    User user1 = User.builder().id(10L).username("user1").organisation(org1).build();
    Investigation invActive = Investigation.builder().id(100L).organisation(org1).investigationKey("INV-1").title("Investigation 1").status("ACTIVE").createdBy(user1).createdAt(Instant.now()).updatedAt(Instant.now()).build();
    Investigation invCompleted = Investigation.builder().id(101L).organisation(org1).investigationKey("INV-2").title("T2").status("COMPLETED").createdBy(user1).createdAt(Instant.now()).updatedAt(Instant.now()).build();
    Investigation invArchived = Investigation.builder().id(102L).organisation(org1).investigationKey("INV-3").title("T3").status("ARCHIVED").createdBy(user1).createdAt(Instant.now()).updatedAt(Instant.now()).build();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new InvestigationReportService(
                investigationRepository, complaintRepository, evidenceLinkRepository, canonicalRepo,
                timelineService, graphReadinessService, authService,
                configurationRepository, definitionRepository
        );
        lenient().when(authService.getCurrentOrgId()).thenReturn(1L);
        lenient().doNothing().when(authService).requireEvidenceGraphAccess();
        lenient().when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(true);
        lenient().when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(invActive));
        lenient().when(investigationRepository.findByIdAndOrganisationOrgId(101L, 1L)).thenReturn(Optional.of(invCompleted));
        lenient().when(investigationRepository.findByIdAndOrganisationOrgId(102L, 1L)).thenReturn(Optional.of(invArchived));
        lenient().when(complaintRepository.findByInvestigationIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.empty());
        lenient().when(timelineService.getTimeline(eq(100L), anyInt(), anyInt())).thenReturn(
                com.taceiq.dto.InvestigationTimelineResponse.builder().investigationId(100L).investigationKey("INV-1").events(List.of()).page(0).size(50).totalElements(0).totalPages(0).build()
        );
        lenient().when(evidenceLinkRepository.countByOrganisationOrgIdAndInvestigationId(1L, 100L)).thenReturn(0L);
        lenient().when(evidenceLinkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of()));
        lenient().when(definitionRepository.findByKey("REPORT_DEFAULT_FORMAT")).thenReturn(Optional.of(ConfigurationDefinition.builder().key("REPORT_DEFAULT_FORMAT").type("ENUM").defaultValue("PDF").build()));
        lenient().when(configurationRepository.findByDefinitionKeyAndOrganisationOrgId("REPORT_DEFAULT_FORMAT", 1L)).thenReturn(Optional.empty());
    }

    // A. BASIC REPORT
    @Test
    void activeGeneratesReport() {
        var resp = service.generateReport(100L, "JSON");
        assertNotNull(resp);
        assertEquals(100L, resp.getReportMetadata().getInvestigationId());
    }

    @Test
    void completedGeneratesReport() {
        lenient().when(complaintRepository.findByInvestigationIdAndOrganisationOrgId(101L, 1L)).thenReturn(Optional.empty());
        lenient().when(evidenceLinkRepository.countByOrganisationOrgIdAndInvestigationId(1L, 101L)).thenReturn(0L);
        lenient().when(evidenceLinkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(101L), any())).thenReturn(new PageImpl<>(List.of()));
        lenient().when(timelineService.getTimeline(eq(101L), anyInt(), anyInt())).thenReturn(
                com.taceiq.dto.InvestigationTimelineResponse.builder().investigationId(101L).investigationKey("INV-2").events(List.of()).build()
        );
        var resp = service.generateReport(101L, "JSON");
        assertNotNull(resp);
    }

    @Test
    void archivedGeneratesReport() {
        lenient().when(complaintRepository.findByInvestigationIdAndOrganisationOrgId(102L, 1L)).thenReturn(Optional.empty());
        lenient().when(evidenceLinkRepository.countByOrganisationOrgIdAndInvestigationId(1L, 102L)).thenReturn(0L);
        lenient().when(evidenceLinkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(102L), any())).thenReturn(new PageImpl<>(List.of()));
        lenient().when(timelineService.getTimeline(eq(102L), anyInt(), anyInt())).thenReturn(
                com.taceiq.dto.InvestigationTimelineResponse.builder().investigationId(102L).investigationKey("INV-3").events(List.of()).build()
        );
        var resp = service.generateReport(102L, "JSON");
        assertNotNull(resp);
    }

    // B. GRAPH READINESS
    @Test
    void graphNotReady400() {
        when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(false);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.generateReport(100L, "JSON"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void graphReadyProceeds() {
        assertDoesNotThrow(() -> service.generateReport(100L, "JSON"));
    }

    // C. TENANT ISOLATION
    @Test
    void anotherOrgInvestigation404() {
        when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.generateReport(100L, "JSON"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void noCrossTenantEvidence() {
        service.generateReport(100L, "JSON");
        verify(evidenceLinkRepository).findByOrganisationOrgIdAndInvestigationId(eq(1L), anyLong(), any());
        verify(evidenceLinkRepository, never()).findByOrganisationOrgIdAndInvestigationId(eq(2L), anyLong(), any());
    }

    // D. CONTENT
    @Test
    void complaintIncludedWhenPresent() {
        Complaint c = Complaint.builder().id(200L).organisation(org1).complaintKey("C-1").title("CTitle").investigation(invActive).build();
        when(complaintRepository.findByInvestigationIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(c));
        var resp = service.generateReport(100L, "JSON");
        assertNotNull(resp.getComplaint());
        assertEquals("C-1", resp.getComplaint().getComplaintKey());
    }

    @Test
    void complaintNullWhenAbsent() {
        when(complaintRepository.findByInvestigationIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.empty());
        var resp = service.generateReport(100L, "JSON");
        assertNull(resp.getComplaint());
        // should not fail
        assertNotNull(resp);
    }

    @Test
    void investigationIncluded() {
        var resp = service.generateReport(100L, "JSON");
        assertNotNull(resp.getInvestigation());
        assertEquals("INV-1", resp.getInvestigation().getInvestigationKey());
    }

    @Test
    void timelineIncluded() {
        var resp = service.generateReport(100L, "JSON");
        assertNotNull(resp.getTimeline());
    }

    @Test
    void evidenceIncluded() {
        CanonicalEvidence ce = CanonicalEvidence.builder().id(300L).organisation(org1).externalId("ev_001").title("Ev Title").build();
        InvestigationEvidence link = InvestigationEvidence.builder().id(1L).organisation(org1).investigation(invActive).canonicalEvidence(ce).build();
        when(evidenceLinkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of(link)));
        when(evidenceLinkRepository.countByOrganisationOrgIdAndInvestigationId(1L, 100L)).thenReturn(1L);
        var resp = service.generateReport(100L, "JSON");
        assertEquals(1, resp.getEvidence().size());
        assertEquals("ev_001", resp.getEvidence().get(0).getStableId());
    }

    // F. FORMAT
    @Test
    void explicitJsonWorks() {
        var resp = service.generateReport(100L, "JSON");
        assertEquals("JSON", resp.getReportMetadata().getFormat());
    }

    @Test
    void explicitCsvWorks() {
        var resp = service.generateReport(100L, "CSV");
        assertEquals("CSV", resp.getReportMetadata().getFormat());
        String csv = service.renderCsv(resp);
        assertTrue(csv.contains("SECTION"));
    }

    @Test
    void explicitPdfWorks() {
        var resp = service.generateReport(100L, "PDF");
        assertEquals("PDF", resp.getReportMetadata().getFormat());
        byte[] pdf = service.renderPdf(resp);
        assertTrue(pdf.length > 0);
        assertTrue(new String(pdf, 0, 4).contains("%PDF"));
    }

    @Test
    void omittedUsesDefault() {
        // Default is PDF per seeder
        var resp = service.generateReport(100L, null);
        assertEquals("PDF", resp.getReportMetadata().getFormat());
    }

    @Test
    void invalidFormat400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.generateReport(100L, "HTML"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    // G. SECURITY
    @Test
    void noOrgIdInResponse() {
        var resp = service.generateReport(100L, "JSON");
        String json = resp.toString();
        assertFalse(json.contains("orgId"));
    }

    @Test
    void noNormalizedPayload() {
        var resp = service.generateReport(100L, "JSON");
        // Ensure no contentHash
        String json = resp.toString();
        assertFalse(json.toLowerCase().contains("contenthash"));
        assertFalse(json.toLowerCase().contains("normalizedpayload"));
    }

    @Test
    void noStorageRef() {
        var resp = service.generateReport(100L, "JSON");
        assertFalse(resp.toString().toLowerCase().contains("storageref"));
    }

    // H. READ ONLY
    @Test
    void doesNotPersistReport() {
        service.generateReport(100L, "JSON");
        verify(investigationRepository, never()).save(any());
    }

    // I. BOUNDS
    @Test
    void timelineLimitEnforced() {
        // Simulate timeline with 1001 events -> should 409
        var manyEvents = new java.util.ArrayList<com.taceiq.dto.InvestigationTimelineEventResponse>();
        for (int i=0;i<1001;i++) manyEvents.add(com.taceiq.dto.InvestigationTimelineEventResponse.builder().eventType("E").eventTime(Instant.now().toString()).title("t").build());
        when(timelineService.getTimeline(eq(100L), anyInt(), anyInt())).thenReturn(
                com.taceiq.dto.InvestigationTimelineResponse.builder().investigationId(100L).investigationKey("INV-1").events(manyEvents).totalElements(1001).totalPages(1).build()
        );
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.generateReport(100L, "JSON"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    // J. DETERMINISM
    @Test
    void sameStateProducesSameStructure() {
        var r1 = service.generateReport(100L, "JSON");
        var r2 = service.generateReport(100L, "JSON");
        assertEquals(r1.getInvestigation().getInvestigationKey(), r2.getInvestigation().getInvestigationKey());
        assertEquals(r1.getEvidence().size(), r2.getEvidence().size());
        assertNotNull(r1.getReportMetadata().getGeneratedAt());
        assertNotNull(r2.getReportMetadata().getGeneratedAt());
        // structure same, generatedAt may be same if within same millis – just check not null
    }
}
