package com.taceiq;

import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.File;
import com.taceiq.entity.Integration;
import com.taceiq.entity.IntegrationSync;
import com.taceiq.entity.Investigation;
import com.taceiq.entity.Organisation;
import com.taceiq.integration.EvidenceNormalizer;
import com.taceiq.integration.dto.ExternalEvidenceRecord;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.FileRepository;
import com.taceiq.repository.IntegrationRepository;
import com.taceiq.repository.IntegrationSyncRepository;
import com.taceiq.repository.InvestigationRepository;
import com.taceiq.repository.OrganisationRepository;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.FileService;
import com.taceiq.service.IntegrationSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class IncidentCanonicalPhase1Test {

    Organisation orgA, orgB;
    Investigation incidentA, incidentB;
    Integration integrationA;

    // Manual upload mocks
    FileRepository fileRepository;
    CanonicalEvidenceRepository canonicalRepo;
    OrganisationRepository orgRepo;
    IntegrationRepository integRepo;
    InvestigationRepository investigationRepo;
    FileService fileService;

    // Integration sync mocks
    IntegrationRepository intRepo2;
    IntegrationSyncRepository syncRepo;
    OrganisationRepository orgRepo2;
    CanonicalEvidenceRepository canonicalRepo2;
    InvestigationRepository invRepo2;
    AuthorizationService authz;
    EvidenceNormalizer normalizer;
    IntegrationSyncService syncService;

    @BeforeEach
    void setup() {
        orgA = Organisation.builder().orgId(1L).name("OrgA").build();
        orgB = Organisation.builder().orgId(2L).name("OrgB").build();
        incidentA = Investigation.builder().id(10L).organisation(orgA).investigationKey("INC-001").title("Incident A").status("ACTIVE").build();
        incidentB = Investigation.builder().id(20L).organisation(orgB).investigationKey("INC-002").title("Incident B").status("ACTIVE").build();
        integrationA = Integration.builder().id(100L).name("IntA").type("API").status("ACTIVE").configuration("{\"baseUrl\":\"https://example.com\",\"token\":\"tok\"}").organisation(orgA).build();

        // Manual upload setup
        fileRepository = mock(FileRepository.class);
        canonicalRepo = mock(CanonicalEvidenceRepository.class);
        orgRepo = mock(OrganisationRepository.class);
        integRepo = mock(IntegrationRepository.class);
        investigationRepo = mock(InvestigationRepository.class);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var publisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        fileService = new FileService(fileRepository, orgRepo, integRepo, canonicalRepo, investigationRepo, mapper, publisher);
        org.springframework.test.util.ReflectionTestUtils.setField(fileService, "storagePath", System.getProperty("java.io.tmpdir") + "/taceiq_incident_test");
        org.springframework.test.util.ReflectionTestUtils.setField(fileService, "maxSizeMb", 10);
        when(orgRepo.getReferenceById(1L)).thenReturn(orgA);
        when(orgRepo.getReferenceById(2L)).thenReturn(orgB);
        when(fileRepository.save(any(File.class))).thenAnswer(i -> { File f=i.getArgument(0); f.setId(100L); return f; });
        when(canonicalRepo.save(any(CanonicalEvidence.class))).thenAnswer(i -> i.getArgument(0));
        when(investigationRepo.findByIdAndOrganisationOrgId(10L, 1L)).thenReturn(Optional.of(incidentA));
        when(investigationRepo.findByIdAndOrganisationOrgId(20L, 2L)).thenReturn(Optional.of(incidentB));
        when(investigationRepo.findByIdAndOrganisationOrgId(20L, 1L)).thenReturn(Optional.empty());
        when(investigationRepo.findByIdAndOrganisationOrgId(10L, 2L)).thenReturn(Optional.empty());
        when(investigationRepo.findById(10L)).thenReturn(Optional.of(incidentA));
        when(investigationRepo.findById(20L)).thenReturn(Optional.of(incidentB));

        // Integration sync setup
        intRepo2 = mock(IntegrationRepository.class);
        syncRepo = mock(IntegrationSyncRepository.class);
        orgRepo2 = mock(OrganisationRepository.class);
        canonicalRepo2 = mock(CanonicalEvidenceRepository.class);
        invRepo2 = mock(InvestigationRepository.class);
        authz = mock(AuthorizationService.class);
        lenient().when(authz.getCurrentOrgId()).thenReturn(1L);
        lenient().doNothing().when(authz).requireIntegrationRead();
        normalizer = new EvidenceNormalizer();
        syncService = new IntegrationSyncService(intRepo2, syncRepo, orgRepo2, authz, null, canonicalRepo2, normalizer, null, null, invRepo2);
        lenient().when(invRepo2.findByIdAndOrganisationOrgId(10L, 1L)).thenReturn(Optional.of(incidentA));
        lenient().when(invRepo2.findByIdAndOrganisationOrgId(20L, 1L)).thenReturn(Optional.empty());
        lenient().when(invRepo2.findById(20L)).thenReturn(Optional.of(incidentB));
        lenient().when(invRepo2.findById(10L)).thenReturn(Optional.of(incidentA));
    }

    // --- Manual upload ---

    @Test
    void manualUploadWithIncidentId_assignsIncident() {
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "hello".getBytes());
        File saved = fileService.uploadFile(file, 1L, 10L);
        assertNotNull(saved);
        ArgumentCaptor<CanonicalEvidence> cap = ArgumentCaptor.forClass(CanonicalEvidence.class);
        verify(canonicalRepo).save(cap.capture());
        CanonicalEvidence ev = cap.getValue();
        assertNotNull(ev.getIncident());
        assertEquals(10L, ev.getIncident().getId());
        assertEquals(1L, ev.getOrganisation().getOrgId());
        assertNull(ev.getIntegration());
        assertTrue(ev.getExternalId().startsWith("MANUAL_FILE_"));
    }

    @Test
    void manualUploadWithoutIncidentId_incidentNull() {
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "hello".getBytes());
        File saved = fileService.uploadFile(file, 1L, null);
        assertNotNull(saved);
        ArgumentCaptor<CanonicalEvidence> cap = ArgumentCaptor.forClass(CanonicalEvidence.class);
        verify(canonicalRepo).save(cap.capture());
        assertNull(cap.getValue().getIncident());
    }

    @Test
    void manualUploadWithIncidentId_overloadWithoutId_stillNull() {
        // backward compat: single-arg style upload without incidentId
        MockMultipartFile file = new MockMultipartFile("file", "legacy.pdf", "application/pdf", "data".getBytes());
        File saved = fileService.uploadFile(file, 1L);
        ArgumentCaptor<CanonicalEvidence> cap = ArgumentCaptor.forClass(CanonicalEvidence.class);
        verify(canonicalRepo).save(cap.capture());
        assertNull(cap.getValue().getIncident());
    }

    @Test
    void manualUpload_crossTenantIncident_rejected() {
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "hello".getBytes());
        // incidentB belongs to orgB (20L), current org is 1L
        assertThrows(AccessDeniedException.class, () -> fileService.uploadFile(file, 1L, 20L));
        verify(canonicalRepo, never()).save(any());
        verify(fileRepository, never()).save(any());
    }

    @Test
    void manualUpload_missingIncident_notFound() {
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "hello".getBytes());
        when(investigationRepo.findById(999L)).thenReturn(Optional.empty());
        when(investigationRepo.findByIdAndOrganisationOrgId(999L, 1L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> fileService.uploadFile(file, 1L, 999L));
    }

    // --- Integration sync ---

    ExternalEvidenceRecord ev(String id) {
        return ExternalEvidenceRecord.builder()
                .evidenceId(id).caseId("CASE-1001").title("t").sourceType("FILE").status("READY")
                .createdAt("2026-09-01T08:12:33.000Z").updatedAt("2026-09-03T10:15:00.000Z")
                .actor(ExternalEvidenceRecord.Actor.builder().actorId("actor_42").role("SYSTEM").build())
                .relationships(ExternalEvidenceRecord.Relationships.builder().caseId_ref("CASE-1001").actorId_ref("actor_42").build())
                .attributes(ExternalEvidenceRecord.Attributes.builder().size(100L).contentType("application/json").tags(List.of("X")).build())
                .build();
    }

    @Test
    void integrationSyncWithIncidentId_assignsIncidentToCreated() {
        IntegrationSync sync = IntegrationSync.builder().id(500L).organisation(orgA).integration(integrationA).status("RUNNING")
                .recordsFetched(0).recordsCreated(0).recordsUpdated(0).recordsSkipped(0).recordsFailed(0).build();
        when(syncRepo.findByIdAndOrganisationOrgId(500L, 1L)).thenReturn(Optional.of(sync));
        when(syncRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(canonicalRepo2.findByExternalIdAndIntegrationIdAndOrganisationOrgId("ev_001", 100L, 1L)).thenReturn(Optional.empty());

        var resp = syncService.completeSync(500L, List.of(ev("ev_001")), 10L);
        assertEquals("SUCCESS", resp.getStatus());
        ArgumentCaptor<CanonicalEvidence> cap = ArgumentCaptor.forClass(CanonicalEvidence.class);
        verify(canonicalRepo2).save(cap.capture());
        assertNotNull(cap.getValue().getIncident());
        assertEquals(10L, cap.getValue().getIncident().getId());
    }

    @Test
    void integrationSyncWithoutIncidentId_incidentNull() {
        IntegrationSync sync = IntegrationSync.builder().id(500L).organisation(orgA).integration(integrationA).status("RUNNING")
                .recordsFetched(0).recordsCreated(0).recordsUpdated(0).recordsSkipped(0).recordsFailed(0).build();
        when(syncRepo.findByIdAndOrganisationOrgId(500L, 1L)).thenReturn(Optional.of(sync));
        when(syncRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(canonicalRepo2.findByExternalIdAndIntegrationIdAndOrganisationOrgId("ev_001", 100L, 1L)).thenReturn(Optional.empty());

        syncService.completeSync(500L, List.of(ev("ev_001")), null);
        ArgumentCaptor<CanonicalEvidence> cap = ArgumentCaptor.forClass(CanonicalEvidence.class);
        verify(canonicalRepo2).save(cap.capture());
        assertNull(cap.getValue().getIncident());
    }

    @Test
    void integrationSync_crossTenantIncident_rejected() {
        IntegrationSync sync = IntegrationSync.builder().id(500L).organisation(orgA).integration(integrationA).status("RUNNING")
                .recordsFetched(0).recordsCreated(0).recordsUpdated(0).recordsSkipped(0).recordsFailed(0).build();
        when(syncRepo.findByIdAndOrganisationOrgId(500L, 1L)).thenReturn(Optional.of(sync));
        // incidentB belongs to orgB
        assertThrows(AccessDeniedException.class, () -> syncService.completeSync(500L, List.of(ev("ev_001")), 20L));
    }

    @Test
    void integrationSync_incidentUpdated_onHashMatchAndOnUpdate() {
        // existing record with no incident, sync with incident should set incident even when hash matches (skipped path)
        IntegrationSync sync = IntegrationSync.builder().id(500L).organisation(orgA).integration(integrationA).status("RUNNING")
                .recordsFetched(0).recordsCreated(0).recordsUpdated(0).recordsSkipped(0).recordsFailed(0).build();
        when(syncRepo.findByIdAndOrganisationOrgId(500L, 1L)).thenReturn(Optional.of(sync));
        when(syncRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        ExternalEvidenceRecord rec = ev("ev_001");
        var norm = normalizer.normalize(rec);
        CanonicalEvidence existing = CanonicalEvidence.builder().id(1L).organisation(orgA).integration(integrationA).externalId("ev_001").contentHash(norm.contentHash).incident(null).firstSeenAt(java.time.Instant.now().minusSeconds(100)).lastSeenAt(java.time.Instant.now().minusSeconds(100)).build();
        when(canonicalRepo2.findByExternalIdAndIntegrationIdAndOrganisationOrgId("ev_001", 100L, 1L)).thenReturn(Optional.of(existing));

        syncService.completeSync(500L, List.of(rec), 10L);
        assertNotNull(existing.getIncident());
        assertEquals(10L, existing.getIncident().getId());
    }

    @Test
    void legacyBehavior_withoutIncident_stillWorks() {
        // exact same as before: no incident param at all via overload
        IntegrationSync sync = IntegrationSync.builder().id(500L).organisation(orgA).integration(integrationA).status("RUNNING")
                .recordsFetched(0).recordsCreated(0).recordsUpdated(0).recordsSkipped(0).recordsFailed(0).build();
        when(syncRepo.findByIdAndOrganisationOrgId(500L, 1L)).thenReturn(Optional.of(sync));
        when(syncRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(canonicalRepo2.findByExternalIdAndIntegrationIdAndOrganisationOrgId("ev_001", 100L, 1L)).thenReturn(Optional.empty());
        // call overload without incidentId
        var resp = syncService.completeSync(500L, List.of(ev("ev_001")));
        assertEquals("SUCCESS", resp.getStatus());
        ArgumentCaptor<CanonicalEvidence> cap = ArgumentCaptor.forClass(CanonicalEvidence.class);
        verify(canonicalRepo2).save(cap.capture());
        assertNull(cap.getValue().getIncident());
    }
}
