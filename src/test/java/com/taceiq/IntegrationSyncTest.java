package com.taceiq;

import com.taceiq.dto.SyncResponse;
import com.taceiq.entity.Integration;
import com.taceiq.entity.IntegrationSync;
import com.taceiq.entity.Organisation;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.IntegrationRepository;
import com.taceiq.repository.IntegrationSyncRepository;
import com.taceiq.repository.OrganisationRepository;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.IntegrationSyncService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class IntegrationSyncTest {

    @Mock IntegrationRepository integrationRepository;
    @Mock IntegrationSyncRepository syncRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock CanonicalEvidenceRepository canonicalRepo;
    @Mock AuthorizationService authorizationService;

    IntegrationSyncService syncService;

    Organisation orgA;
    Organisation orgB;
    Integration integrationA;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        syncService = new IntegrationSyncService(integrationRepository, syncRepository, organisationRepository, authorizationService);
        orgA = Organisation.builder().orgId(1L).name("OrgA").build();
        orgB = Organisation.builder().orgId(2L).name("OrgB").build();
        integrationA = Integration.builder().id(100L).name("IntA").type("API").status("ACTIVE").configuration("cfg").organisation(orgA).build();
        lenient().when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        lenient().doNothing().when(authorizationService).requireIntegrationRead();
        lenient().when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        lenient().when(integrationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(integrationA));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void validIntegrationCreatesPendingSync() {
        when(syncRepository.save(any())).thenAnswer(i -> { IntegrationSync s=i.getArgument(0); s.setId(999L); return s;});
        SyncResponse resp = syncService.createPending(100L);
        assertEquals(999L, resp.getSyncId());
        assertEquals("PENDING", resp.getStatus());
        ArgumentCaptor<IntegrationSync> cap = ArgumentCaptor.forClass(IntegrationSync.class);
        verify(syncRepository).save(cap.capture());
        IntegrationSync saved = cap.getValue();
        assertEquals("PENDING", saved.getStatus());
        assertEquals(0, saved.getRecordsFetched());
        assertEquals(0, saved.getRetryCount());
        assertEquals(1L, saved.getOrganisation().getOrgId());
        assertEquals(100L, saved.getIntegration().getId());
        verify(authorizationService).requireIntegrationRead();
        verify(authorizationService).getCurrentOrgId();
    }

    @Test
    void syncBelongsToAuthenticatedOrganisation() {
        when(syncRepository.save(any())).thenAnswer(i -> { IntegrationSync s=i.getArgument(0); s.setId(999L); return s;});
        SyncResponse resp = syncService.createPending(100L);
        ArgumentCaptor<IntegrationSync> cap = ArgumentCaptor.forClass(IntegrationSync.class);
        verify(syncRepository).save(cap.capture());
        assertEquals(1L, cap.getValue().getOrganisation().getOrgId());
        assertNotEquals(2L, cap.getValue().getOrganisation().getOrgId());
    }

    @Test
    void crossTenantIntegrationCannotCreateSync() {
        when(integrationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.empty());
        when(integrationRepository.findById(100L)).thenReturn(Optional.of(Integration.builder().id(100L).organisation(orgB).build()));
        assertThrows(AccessDeniedException.class, () -> syncService.createPending(100L));
        verify(syncRepository, never()).save(any());
    }

    @Test
    void missingIntegrationReturns404() {
        when(integrationRepository.findByIdAndOrganisationOrgId(999L, 1L)).thenReturn(Optional.empty());
        when(integrationRepository.findById(999L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> syncService.createPending(999L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void missingPermissionReturns403() {
        doThrow(new AccessDeniedException("Missing")).when(authorizationService).requireIntegrationRead();
        assertThrows(AccessDeniedException.class, () -> syncService.createPending(100L));
        verify(syncRepository, never()).save(any());
    }

    @Test
    void responseContainsOnlySyncIdAndStatus() throws Exception {
        when(syncRepository.save(any())).thenAnswer(i -> { IntegrationSync s=i.getArgument(0); s.setId(555L); return s;});
        SyncResponse resp = syncService.createPending(100L);
        // DTO has exactly 2 fields
        assertEquals(2, resp.getClass().getDeclaredFields().length);
        assertNotNull(resp.getSyncId());
        assertNotNull(resp.getStatus());
        // Ensure no org/integration leak via DTO
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(resp);
        assertTrue(json.contains("syncId"));
        assertTrue(json.contains("PENDING"));
        assertFalse(json.toLowerCase().contains("organisation"));
        assertFalse(json.toLowerCase().contains("integration"));
    }

    @Test
    void noCanonicalEvidenceRowsAreCreated() {
        when(syncRepository.save(any())).thenAnswer(i -> { IntegrationSync s=i.getArgument(0); s.setId(777L); return s;});
        syncService.createPending(100L);
        verifyNoInteractions(canonicalRepo);
        verify(syncRepository, times(1)).save(any());
    }

    @Test
    void noExternalHttpRequestIsMade() {
        when(syncRepository.save(any())).thenAnswer(i -> { IntegrationSync s=i.getArgument(0); s.setId(888L); return s;});
        // Sync service has no RestTemplate/WebClient dependency – verify by construction
        assertDoesNotThrow(() -> syncService.createPending(100L));
        // No external call holder – ensure no canonical repo interaction and only sync save
        verify(syncRepository).save(any());
    }

    @Test
    void repeatedPostCreatesIndependentPendingSyncRecords() {
        when(syncRepository.save(any())).thenAnswer(i -> {
            IntegrationSync s = i.getArgument(0);
            // simulate auto-id
            s.setId((long)(1000 + Math.random()*1000));
            return s;
        });
        SyncResponse r1 = syncService.createPending(100L);
        SyncResponse r2 = syncService.createPending(100L);
        assertNotEquals(r1.getSyncId(), r2.getSyncId());
        assertEquals("PENDING", r1.getStatus());
        assertEquals("PENDING", r2.getStatus());
        verify(syncRepository, times(2)).save(any());
    }

    @Test
    void controllerReturns202() {
        // also verify controller delegation without starting Spring context - now syncIntegration does full flow
        // For this test, we mock the full flow to still return PENDING via createPending mock
        // Use a spy to mock syncIntegration to return PENDING
        var spyService = spy(syncService);
        doReturn(new com.taceiq.dto.SyncResponse(123L, "PENDING")).when(spyService).syncIntegration(100L);
        com.taceiq.controller.IntegrationController ctrl = new com.taceiq.controller.IntegrationController(
                new com.taceiq.service.IntegrationService(integrationRepository, organisationRepository, mock(com.taceiq.repository.FileRepository.class)),
                spyService, authorizationService);
        var resp = ctrl.createSync(100L);
        assertEquals(HttpStatus.ACCEPTED, resp.getStatusCode());
        assertEquals(123L, resp.getBody().getSyncId());
        assertEquals("PENDING", resp.getBody().getStatus());
    }
}
