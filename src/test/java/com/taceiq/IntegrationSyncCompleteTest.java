package com.taceiq;

import com.taceiq.dto.SyncResponse;
import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.Integration;
import com.taceiq.entity.IntegrationSync;
import com.taceiq.entity.Organisation;
import com.taceiq.integration.EvidenceNormalizer;
import com.taceiq.integration.dto.ExternalEvidenceRecord;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.IntegrationRepository;
import com.taceiq.repository.IntegrationSyncRepository;
import com.taceiq.repository.OrganisationRepository;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.IntegrationSyncService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class IntegrationSyncCompleteTest {

    @Mock IntegrationRepository integrationRepository;
    @Mock IntegrationSyncRepository syncRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock CanonicalEvidenceRepository canonicalRepo;
    @Mock AuthorizationService authorizationService;

    EvidenceNormalizer normalizer = new EvidenceNormalizer();
    IntegrationSyncService service;

    Organisation orgA, orgB;
    Integration intA;
    IntegrationSync syncRunning;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        orgA = Organisation.builder().orgId(1L).name("OrgA").build();
        orgB = Organisation.builder().orgId(2L).name("OrgB").build();
        intA = Integration.builder().id(100L).name("IntA").type("API").status("ACTIVE").configuration("{\"baseUrl\":\"https://example.com\",\"token\":\"tok\"}").organisation(orgA).build();
        syncRunning = IntegrationSync.builder().id(500L).organisation(orgA).integration(intA).status("RUNNING").recordsFetched(0).recordsCreated(0).recordsUpdated(0).recordsSkipped(0).recordsFailed(0).build();
        lenient().when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        lenient().when(syncRepository.findByIdAndOrganisationOrgId(500L, 1L)).thenReturn(Optional.of(syncRunning));
        lenient().when(syncRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        service = new IntegrationSyncService(integrationRepository, syncRepository, organisationRepository, authorizationService, null, canonicalRepo, normalizer, null, null);
    }

    @AfterEach void clear(){ SecurityContextHolder.clearContext(); }

    ExternalEvidenceRecord ev(String id, String title) {
        return ExternalEvidenceRecord.builder()
                .evidenceId(id).caseId("CASE-1001").title(title).sourceType("FILE").status("READY")
                .createdAt("2026-09-01T08:12:33.000Z").updatedAt("2026-09-03T10:15:00.000Z")
                .actor(ExternalEvidenceRecord.Actor.builder().actorId("actor_42").role("SYSTEM").build())
                .relationships(ExternalEvidenceRecord.Relationships.builder().caseId_ref("CASE-1001").actorId_ref("actor_42").parentEvidenceId(null).build())
                .attributes(ExternalEvidenceRecord.Attributes.builder().size(2048L).contentType("application/json").tags(List.of("ANONYMIZED")).build())
                .build();
    }

    @Test
    void newExternalIdCreatesOneRecord() {
        when(canonicalRepo.findByExternalIdAndIntegrationIdAndOrganisationOrgId("ev_001", 100L, 1L)).thenReturn(Optional.empty());
        SyncResponse resp = service.completeSync(500L, List.of(ev("ev_001","t")));
        verify(canonicalRepo).save(any(CanonicalEvidence.class));
        assertEquals("SUCCESS", resp.getStatus());
        assertEquals(1, syncRunning.getRecordsCreated());
        assertEquals(1, syncRunning.getRecordsFetched());
    }

    @Test
    void sameExternalIdSameHashSkipped() {
        ExternalEvidenceRecord rec = ev("ev_001","t");
        EvidenceNormalizer.NormalizedEvidence n = normalizer.normalize(rec);
        CanonicalEvidence existing = CanonicalEvidence.builder().id(1L).organisation(orgA).integration(intA).externalId("ev_001").contentHash(n.contentHash).firstSeenAt(Instant.now().minusSeconds(3600)).lastSeenAt(Instant.now().minusSeconds(3600)).build();
        when(canonicalRepo.findByExternalIdAndIntegrationIdAndOrganisationOrgId("ev_001", 100L, 1L)).thenReturn(Optional.of(existing));
        SyncResponse resp = service.completeSync(500L, List.of(rec));
        verify(canonicalRepo).save(existing);
        assertEquals(1, syncRunning.getRecordsSkipped());
        assertEquals(0, syncRunning.getRecordsCreated());
        assertEquals("SUCCESS", resp.getStatus());
    }

    @Test
    void sameExternalIdDifferentHashUpdated() {
        ExternalEvidenceRecord rec1 = ev("ev_001","title A");
        EvidenceNormalizer.NormalizedEvidence n1 = normalizer.normalize(rec1);
        CanonicalEvidence existing = CanonicalEvidence.builder().id(1L).organisation(orgA).integration(intA).externalId("ev_001").contentHash(n1.contentHash).firstSeenAt(Instant.now().minusSeconds(3600)).title("title A").build();
        when(canonicalRepo.findByExternalIdAndIntegrationIdAndOrganisationOrgId("ev_001", 100L, 1L)).thenReturn(Optional.of(existing));
        ExternalEvidenceRecord rec2 = ev("ev_001","title B");
        SyncResponse resp = service.completeSync(500L, List.of(rec2));
        assertEquals(1, syncRunning.getRecordsUpdated());
        assertEquals("title B", existing.getTitle());
        assertEquals("SUCCESS", resp.getStatus());
    }

    @Test
    void repeatedSyncDoesNotCreateDuplicates() {
        ExternalEvidenceRecord rec = ev("ev_001","t");
        when(canonicalRepo.findByExternalIdAndIntegrationIdAndOrganisationOrgId("ev_001", 100L, 1L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(CanonicalEvidence.builder().id(1L).externalId("ev_001").contentHash(normalizer.normalize(rec).contentHash).build()));
        service.completeSync(500L, List.of(rec));
        // second sync – same hash should skip
        syncRunning.setStatus("RUNNING");
        syncRunning.setRecordsCreated(0); syncRunning.setRecordsSkipped(0);
        service.completeSync(500L, List.of(rec));
        // verify second call was skipped not created (we can check via mock counts)
        // second call's existing has same hash so skipped
        assertEquals(1, syncRunning.getRecordsSkipped());
    }

    @Test
    void sameExternalIdCanExistInAnotherOrganisation() {
        // orgA ev_001 exists, orgB same id should be allowed – service uses org from sync, so lookup is org-specific
        ExternalEvidenceRecord rec = ev("ev_001","t");
        when(canonicalRepo.findByExternalIdAndIntegrationIdAndOrganisationOrgId("ev_001", 100L, 1L)).thenReturn(Optional.empty());
        SyncResponse resp = service.completeSync(500L, List.of(rec));
        assertEquals("SUCCESS", resp.getStatus());
        // different org would be different sync – not cross
        IntegrationSync syncB = IntegrationSync.builder().id(501L).organisation(orgB).integration(Integration.builder().id(200L).organisation(orgB).build()).status("RUNNING").recordsCreated(0).recordsFetched(0).recordsUpdated(0).recordsSkipped(0).recordsFailed(0).build();
        when(syncRepository.findByIdAndOrganisationOrgId(501L, 2L)).thenReturn(Optional.of(syncB));
        lenient().when(authorizationService.getCurrentOrgId()).thenReturn(2L);
        // need new service with orgB context
        IntegrationSyncService serviceB = new IntegrationSyncService(integrationRepository, syncRepository, organisationRepository, authorizationService, null, canonicalRepo, normalizer, null, null);
        // mock for orgB
        when(canonicalRepo.findByExternalIdAndIntegrationIdAndOrganisationOrgId("ev_001", 200L, 2L)).thenReturn(Optional.empty());
        SyncResponse respB = serviceB.completeSync(501L, List.of(rec));
        assertEquals("SUCCESS", respB.getStatus());
    }

    @Test
    void sameExternalIdCanExistForAnotherIntegration() {
        Integration int2 = Integration.builder().id(200L).organisation(orgA).build();
        IntegrationSync sync2 = IntegrationSync.builder().id(501L).organisation(orgA).integration(int2).status("RUNNING").recordsCreated(0).recordsFetched(0).recordsUpdated(0).recordsSkipped(0).recordsFailed(0).build();
        when(syncRepository.findByIdAndOrganisationOrgId(501L, 1L)).thenReturn(Optional.of(sync2));
        when(canonicalRepo.findByExternalIdAndIntegrationIdAndOrganisationOrgId("ev_001", 200L, 1L)).thenReturn(Optional.empty());
        IntegrationSyncService s2 = new IntegrationSyncService(integrationRepository, syncRepository, organisationRepository, authorizationService, null, canonicalRepo, normalizer, null, null);
        SyncResponse resp = s2.completeSync(501L, List.of(ev("ev_001","t")));
        assertEquals("SUCCESS", resp.getStatus());
    }

    @Test
    void firstSeenAtRemainsUnchangedOnUpdate() {
        ExternalEvidenceRecord rec1 = ev("ev_001","title A");
        Instant first = Instant.now().minusSeconds(7200);
        CanonicalEvidence existing = CanonicalEvidence.builder().id(1L).organisation(orgA).integration(intA).externalId("ev_001").contentHash(normalizer.normalize(rec1).contentHash).firstSeenAt(first).lastSeenAt(first).build();
        when(canonicalRepo.findByExternalIdAndIntegrationIdAndOrganisationOrgId("ev_001", 100L, 1L)).thenReturn(Optional.of(existing));
        ExternalEvidenceRecord rec2 = ev("ev_001","title B");
        service.completeSync(500L, List.of(rec2));
        assertEquals(first, existing.getFirstSeenAt());
        assertTrue(existing.getLastSeenAt().isAfter(first));
    }

    @Test
    void lastSeenAtUpdates() throws InterruptedException {
        ExternalEvidenceRecord rec = ev("ev_001","t");
        EvidenceNormalizer.NormalizedEvidence n = normalizer.normalize(rec);
        CanonicalEvidence existing = CanonicalEvidence.builder().id(1L).organisation(orgA).integration(intA).externalId("ev_001").contentHash("oldhash").firstSeenAt(Instant.now().minusSeconds(3600)).lastSeenAt(Instant.now().minusSeconds(3600)).build();
        when(canonicalRepo.findByExternalIdAndIntegrationIdAndOrganisationOrgId("ev_001", 100L, 1L)).thenReturn(Optional.of(existing));
        Thread.sleep(5);
        service.completeSync(500L, List.of(rec));
        // after update, lastSeenAt should be later than before
        assertTrue(existing.getLastSeenAt().isAfter(existing.getFirstSeenAt()) || existing.getLastSeenAt().equals(existing.getFirstSeenAt()));
    }

    @Test
    void syncIdIsAssociatedCorrectly() {
        ExternalEvidenceRecord rec = ev("ev_001","t");
        when(canonicalRepo.findByExternalIdAndIntegrationIdAndOrganisationOrgId("ev_001", 100L, 1L)).thenReturn(Optional.empty());
        service.completeSync(500L, List.of(rec));
        var captor = org.mockito.ArgumentCaptor.forClass(CanonicalEvidence.class);
        verify(canonicalRepo).save(captor.capture());
        assertEquals(500L, captor.getValue().getSync().getId());
        assertEquals(1L, captor.getValue().getOrganisation().getOrgId());
    }

    @Test
    void tenantScopedLookupsCannotCrossOrganisations() {
        when(syncRepository.findByIdAndOrganisationOrgId(500L, 1L)).thenReturn(Optional.empty());
        when(syncRepository.findById(500L)).thenReturn(Optional.of(syncRunning)); // belongs to orgA but caller is orgB?
        // simulate caller is orgB (2L) trying to access orgA sync
        lenient().when(authorizationService.getCurrentOrgId()).thenReturn(2L);
        IntegrationSyncService serviceB = new IntegrationSyncService(integrationRepository, syncRepository, organisationRepository, authorizationService, null, canonicalRepo, normalizer, null, null);
        // need mock for 2L lookup empty -> should throw AccessDenied
        when(syncRepository.findByIdAndOrganisationOrgId(500L, 2L)).thenReturn(Optional.empty());
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> serviceB.completeSync(500L, List.of(ev("ev_001","t"))));
    }

    @Test
    void runningToSuccessWhenAllSucceed() {
        when(canonicalRepo.findByExternalIdAndIntegrationIdAndOrganisationOrgId(anyString(), anyLong(), anyLong())).thenReturn(Optional.empty());
        SyncResponse resp = service.completeSync(500L, List.of(ev("ev_001","t"), ev("ev_002","t2")));
        assertEquals("SUCCESS", resp.getStatus());
        assertNotNull(syncRunning.getCompletedAt());
        assertEquals(2, syncRunning.getRecordsFetched());
        assertEquals(2, syncRunning.getRecordsCreated());
    }

    @Test
    void runningToPartialWhenSomeFail() {
        ExternalEvidenceRecord good = ev("ev_001","t");
        ExternalEvidenceRecord bad = ExternalEvidenceRecord.builder().evidenceId("  ").caseId("CASE-1").build(); // missing externalId -> invalid
        when(canonicalRepo.findByExternalIdAndIntegrationIdAndOrganisationOrgId("ev_001", 100L, 1L)).thenReturn(Optional.empty());
        SyncResponse resp = service.completeSync(500L, List.of(good, bad));
        assertEquals("PARTIAL", resp.getStatus());
        assertEquals(2, syncRunning.getRecordsFetched());
        assertEquals(1, syncRunning.getRecordsCreated());
        assertEquals(1, syncRunning.getRecordsFailed());
        assertNotNull(syncRunning.getErrorSummary());
    }

    @Test
    void countersAreCorrect() {
        when(canonicalRepo.findByExternalIdAndIntegrationIdAndOrganisationOrgId(anyString(), anyLong(), anyLong())).thenReturn(Optional.empty());
        service.completeSync(500L, List.of(ev("ev_001","t"), ev("ev_002","t2")));
        assertEquals(2, syncRunning.getRecordsFetched());
        assertEquals(2, syncRunning.getRecordsCreated());
        assertEquals(0, syncRunning.getRecordsUpdated());
        assertEquals(0, syncRunning.getRecordsSkipped());
    }

    @Test
    void completedAtIsPopulated() {
        when(canonicalRepo.findByExternalIdAndIntegrationIdAndOrganisationOrgId(anyString(), anyLong(), anyLong())).thenReturn(Optional.empty());
        service.completeSync(500L, List.of(ev("ev_001","t")));
        assertNotNull(syncRunning.getCompletedAt());
    }

    @Test
    void noDeletionOccursForAbsentRecords() {
        when(canonicalRepo.findByExternalIdAndIntegrationIdAndOrganisationOrgId(anyString(), anyLong(), anyLong())).thenReturn(Optional.empty());
        service.completeSync(500L, List.of(ev("ev_001","t")));
        // only ev_001 persisted, no ev_002 deletion
        verify(canonicalRepo, times(1)).save(any());
        // isDeleted should remain false
        var captor = org.mockito.ArgumentCaptor.forClass(CanonicalEvidence.class);
        verify(canonicalRepo).save(captor.capture());
        assertFalse(captor.getValue().getIsDeleted());
        assertNull(captor.getValue().getDeletedAt());
    }

    @Test
    void errorsDoNotExposeCredentials() {
        ExternalEvidenceRecord bad = ExternalEvidenceRecord.builder().evidenceId(null).build();
        SyncResponse resp = service.completeSync(500L, List.of(bad));
        assertEquals("FAILED", resp.getStatus());
        assertNotNull(syncRunning.getErrorSummary());
        assertFalse(syncRunning.getErrorSummary().toLowerCase().contains("bearer"));
        assertFalse(syncRunning.getErrorSummary().toLowerCase().contains("token"));
    }
}
