package com.taceiq;

import com.taceiq.controller.IntegrationController;
import com.taceiq.entity.File;
import com.taceiq.entity.Integration;
import com.taceiq.entity.Organisation;
import com.taceiq.repository.FileRepository;
import com.taceiq.repository.IntegrationRepository;
import com.taceiq.repository.OrganisationRepository;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.IntegrationService;
import com.taceiq.service.IntegrationSyncService;
import com.taceiq.repository.IntegrationSyncRepository;
import com.taceiq.repository.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class IntegrationManagementTest {

    @Mock IntegrationRepository integrationRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock FileRepository fileRepository;
    @Mock IntegrationSyncRepository syncRepository;
    @Mock AuthorizationService authorizationService;

    IntegrationService integrationService;
    IntegrationSyncService syncService;
    IntegrationController integrationController;

    Organisation orgA;
    Organisation orgB;
    Integration integrationA;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        integrationService = new IntegrationService(integrationRepository, organisationRepository, fileRepository);
        syncService = new IntegrationSyncService(integrationRepository, syncRepository, organisationRepository, authorizationService);
        integrationController = new IntegrationController(integrationService, syncService, authorizationService);

        orgA = Organisation.builder().orgId(1L).name("OrgA").build();
        orgB = Organisation.builder().orgId(2L).name("OrgB").build();
        integrationA = Integration.builder().id(100L).name("IntA").type("API").status("ACTIVE").configuration("{\"key\":\"secret\"}").organisation(orgA).build();

        lenient().doNothing().when(authorizationService).requireIntegrationCreate();
        lenient().doNothing().when(authorizationService).requireIntegrationRead();
        lenient().doNothing().when(authorizationService).requireIntegrationUpdate();
        lenient().doNothing().when(authorizationService).requireIntegrationDelete();
        lenient().when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        lenient().when(authorizationService.isPlatformAdmin()).thenReturn(false);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    void setAuth(String username, String... authorities) {
        List<SimpleGrantedAuthority> granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(username, null, granted);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // --- Create ---
    @Test
    void create_success() {
        when(integrationRepository.existsByNameAndOrganisationOrgId("IntA", 1L)).thenReturn(false);
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(integrationRepository.save(any())).thenAnswer(i -> {
            Integration in = i.getArgument(0);
            in.setId(101L);
            return in;
        });
        Integration payload = Integration.builder().name("IntA").type("API").status("ACTIVE").configuration("cfg").build();
        Integration created = integrationService.createIntegration(payload, 1L);
        assertEquals("IntA", created.getName());
        assertEquals(1L, created.getOrganisation().getOrgId());
    }

    @Test
    void create_duplicateName_409() {
        when(integrationRepository.existsByNameAndOrganisationOrgId("IntA", 1L)).thenReturn(true);
        Integration payload = Integration.builder().name("IntA").type("API").build();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> integrationService.createIntegration(payload, 1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void create_blankName_400() {
        Integration payload1 = Integration.builder().name("   ").type("API").build();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> integrationService.createIntegration(payload1, 1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        Integration payload2 = Integration.builder().name(null).type("API").build();
        ex = assertThrows(ResponseStatusException.class, () -> integrationService.createIntegration(payload2, 1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void create_invalidStatus_400() {
        Integration payload = Integration.builder().name("IntA").type("API").status("INVALID_STATUS").build();
        when(integrationRepository.existsByNameAndOrganisationOrgId("IntA", 1L)).thenReturn(false);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> integrationService.createIntegration(payload, 1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void create_forcesOrganization() {
        when(integrationRepository.existsByNameAndOrganisationOrgId("Forced", 1L)).thenReturn(false);
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(integrationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Integration payload = Integration.builder().name("Forced").type("API").organisation(orgB).build(); // client tries orgB
        Integration created = integrationService.createIntegration(payload, 1L);
        assertEquals(1L, created.getOrganisation().getOrgId());
        assertNotEquals(2L, created.getOrganisation().getOrgId());
    }

    @Test
    void create_viaController_forcesOrgAndRequiresPermission() {
        when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        when(integrationRepository.existsByNameAndOrganisationOrgId("ViaCtrl", 1L)).thenReturn(false);
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(integrationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Integration payload = Integration.builder().name("ViaCtrl").type("API").organisation(orgB).build();
        var resp = integrationController.createIntegration(payload);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals(1L, resp.getBody().getOrganisation().getOrgId());
        verify(authorizationService).requireIntegrationCreate();
    }

    // --- Read ---
    @Test
    void read_byId_success() {
        when(integrationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(integrationA));
        Integration found = integrationService.getIntegrationById(100L, 1L);
        assertEquals(100L, found.getId());
    }

    @Test
    void read_byId_crossOrg_403() {
        when(integrationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.empty());
        when(integrationRepository.findById(100L)).thenReturn(Optional.of(Integration.builder().id(100L).organisation(orgB).name("IntB").build()));
        assertThrows(AccessDeniedException.class, () -> integrationService.getIntegrationById(100L, 1L));
        // via controller also 403
        when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        when(integrationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.empty());
        when(integrationRepository.findById(100L)).thenReturn(Optional.of(Integration.builder().id(100L).organisation(orgB).build()));
        assertThrows(AccessDeniedException.class, () -> integrationController.getIntegrationById(100L));
    }

    @Test
    void read_byId_notFound_404() {
        when(integrationRepository.findByIdAndOrganisationOrgId(999L, 1L)).thenReturn(Optional.empty());
        when(integrationRepository.findById(999L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> integrationService.getIntegrationById(999L, 1L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void read_byName_success() {
        when(integrationRepository.findByNameAndOrganisationOrgId("IntA", 1L)).thenReturn(Optional.of(integrationA));
        Integration found = integrationService.getIntegrationByName("IntA", 1L);
        assertEquals("IntA", found.getName());
    }

    @Test
    void read_byName_crossOrg_404() {
        // by-name is org-scoped, so cross-org appears as not found 404 (not 403) - but we should still not leak
        when(integrationRepository.findByNameAndOrganisationOrgId("IntB", 1L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> integrationService.getIntegrationByName("IntB", 1L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void list_onlyCurrentOrg() {
        Integration intB = Integration.builder().id(200L).name("IntB").organisation(orgB).build();
        when(integrationRepository.findByOrganisationOrgId(1L)).thenReturn(List.of(integrationA));
        when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        var resp = integrationController.getIntegrationsByOrganisation();
        assertEquals(1, resp.getBody().size());
        assertEquals(1L, resp.getBody().get(0).getOrganisation().getOrgId());
        verify(authorizationService).requireIntegrationRead();
    }

    // --- Update ---
    @Test
    void update_success() {
        when(integrationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(integrationA));
        when(integrationRepository.existsByNameAndOrganisationOrgId("NewName", 1L)).thenReturn(false);
        when(integrationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Integration payload = Integration.builder().name("NewName").type("WEBHOOK").status("INACTIVE").configuration("newCfg").build();
        Integration updated = integrationService.updateIntegration(100L, 1L, payload);
        assertEquals("NewName", updated.getName());
        assertEquals("WEBHOOK", updated.getType());
        assertEquals("INACTIVE", updated.getStatus());
    }

    @Test
    void update_duplicateName_409() {
        when(integrationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(integrationA));
        when(integrationRepository.existsByNameAndOrganisationOrgId("Dup", 1L)).thenReturn(true);
        Integration payload = Integration.builder().name("Dup").build();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> integrationService.updateIntegration(100L, 1L, payload));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void update_organizationCannotChange() {
        when(integrationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(integrationA));
        when(integrationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Integration payload = Integration.builder().name("IntA").organisation(orgB).type("API").build(); // try to change org
        Integration updated = integrationService.updateIntegration(100L, 1L, payload);
        assertEquals(1L, updated.getOrganisation().getOrgId(), "Org should not change");
    }

    @Test
    void update_crossOrg_403() {
        when(integrationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.empty());
        when(integrationRepository.findById(100L)).thenReturn(Optional.of(Integration.builder().id(100L).organisation(orgB).build()));
        Integration payload = Integration.builder().name("Hacked").build();
        assertThrows(AccessDeniedException.class, () -> integrationService.updateIntegration(100L, 1L, payload));
    }

    @Test
    void update_blankName_400() {
        when(integrationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(integrationA));
        Integration payload = Integration.builder().name("   ").build();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> integrationService.updateIntegration(100L, 1L, payload));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void update_invalidStatus_400() {
        when(integrationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(integrationA));
        Integration payload = Integration.builder().status("BAD_STATUS").build();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> integrationService.updateIntegration(100L, 1L, payload));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    // --- Status update ---
    @Test
    void statusUpdate_success() {
        when(integrationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(integrationA));
        when(integrationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Integration updated = integrationService.updateIntegrationStatus(100L, 1L, "DISABLED");
        assertEquals("DISABLED", updated.getStatus());
    }

    @Test
    void statusUpdate_invalid_400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> integrationService.updateIntegrationStatus(100L, 1L, "INVALID"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        ex = assertThrows(ResponseStatusException.class, () -> integrationService.updateIntegrationStatus(100L, 1L, " "));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void statusUpdate_viaController() {
        when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        when(integrationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(integrationA));
        when(integrationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var resp = integrationController.updateIntegrationStatus(100L, Map.of("status", "ACTIVE"));
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("ACTIVE", resp.getBody().getStatus());
        verify(authorizationService).requireIntegrationUpdate();
    }

    // --- Delete ---
    @Test
    void delete_success_noFiles() {
        when(integrationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(integrationA));
        when(fileRepository.findByIntegrationIdAndOrganisationOrgId(100L, 1L)).thenReturn(List.of());
        assertDoesNotThrow(() -> integrationService.deleteIntegration(100L, 1L));
        verify(integrationRepository).delete(integrationA);
    }

    @Test
    void delete_withFiles_409() {
        when(integrationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(integrationA));
        File f = File.builder().id(1L).integration(integrationA).organisation(orgA).sourceType("MANUAL_UPLOAD").build();
        when(fileRepository.findByIntegrationIdAndOrganisationOrgId(100L, 1L)).thenReturn(List.of(f));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> integrationService.deleteIntegration(100L, 1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("files"));
    }

    @Test
    void delete_crossOrg_403() {
        when(integrationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.empty());
        when(integrationRepository.findById(100L)).thenReturn(Optional.of(Integration.builder().id(100L).organisation(orgB).build()));
        assertThrows(AccessDeniedException.class, () -> integrationService.deleteIntegration(100L, 1L));
    }

    // --- Test endpoint ---
    @Test
    void testConnection_success() {
        when(integrationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(integrationA));
        String msg = integrationService.testConnection(100L, 1L);
        assertTrue(msg.contains("IntA"));
        assertFalse(msg.contains("secret"), "Should not expose configuration/secrets");
        assertFalse(msg.toLowerCase().contains("secret"));
    }

    @Test
    void testConnection_missingConfig_400() {
        Integration noCfg = Integration.builder().id(100L).name("IntA").type("API").configuration(" ").organisation(orgA).build();
        when(integrationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(noCfg));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> integrationService.testConnection(100L, 1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void testConnection_missingType_400() {
        Integration noType = Integration.builder().id(100L).name("IntA").configuration("cfg").organisation(orgA).build();
        when(integrationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(noType));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> integrationService.testConnection(100L, 1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void testConnection_crossOrg_403() {
        when(integrationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.empty());
        when(integrationRepository.findById(100L)).thenReturn(Optional.of(Integration.builder().id(100L).organisation(orgB).build()));
        assertThrows(AccessDeniedException.class, () -> integrationService.testConnection(100L, 1L));
        // via controller
        when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        when(integrationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.empty());
        when(integrationRepository.findById(100L)).thenReturn(Optional.of(Integration.builder().id(100L).organisation(orgB).build()));
        assertThrows(AccessDeniedException.class, () -> integrationController.testConnection(100L));
    }

    @Test
    void testConnection_viaController_requiresPermission() {
        doThrow(new AccessDeniedException("Missing")).when(authorizationService).requireIntegrationRead();
        when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        assertThrows(AccessDeniedException.class, () -> integrationController.testConnection(100L));
        verify(authorizationService).requireIntegrationRead();
    }

    // --- Missing permissions ---
    @Test
    void missingPermission_403() {
        doThrow(new AccessDeniedException("Missing")).when(authorizationService).requireIntegrationCreate();
        when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        assertThrows(AccessDeniedException.class, () -> integrationController.createIntegration(Integration.builder().name("x").build()));
        doThrow(new AccessDeniedException("Missing")).when(authorizationService).requireIntegrationRead();
        assertThrows(AccessDeniedException.class, () -> integrationController.getIntegrationById(100L));
        doThrow(new AccessDeniedException("Missing")).when(authorizationService).requireIntegrationUpdate();
        assertThrows(AccessDeniedException.class, () -> integrationController.updateIntegration(100L, Integration.builder().name("x").build()));
        doThrow(new AccessDeniedException("Missing")).when(authorizationService).requireIntegrationDelete();
        assertThrows(AccessDeniedException.class, () -> integrationController.deleteIntegration(100L));
    }

    // --- Platform Admin isolation ---
    @Test
    void platformAdmin_403() {
        when(authorizationService.getCurrentOrgId()).thenThrow(new AccessDeniedException("Platform Admin has no organisation scope"));
        when(authorizationService.isPlatformAdmin()).thenReturn(true);
        assertThrows(AccessDeniedException.class, () -> integrationController.createIntegration(Integration.builder().name("x").build()));
        assertThrows(AccessDeniedException.class, () -> integrationController.getIntegrationsByOrganisation());
        assertThrows(AccessDeniedException.class, () -> integrationController.getIntegrationById(100L));
        assertThrows(AccessDeniedException.class, () -> integrationController.updateIntegration(100L, Integration.builder().name("x").build()));
        assertThrows(AccessDeniedException.class, () -> integrationController.deleteIntegration(100L));
        assertThrows(AccessDeniedException.class, () -> integrationController.testConnection(100L));
    }

    // --- Integration/File relationship ---
    @Test
    void fileRelationship_preventUnsafeDelete() {
        // Already covered in delete_withFiles_409, but also test that file org mismatch is checked via IntegrationService
        // Ensure that File's integration org is same as integration org
        Integration intA = Integration.builder().id(100L).organisation(orgA).name("IntA").build();
        File file = File.builder().id(1L).organisation(orgA).integration(intA).sourceType("INTEGRATION").build();
        when(integrationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(intA));
        when(fileRepository.findByIntegrationIdAndOrganisationOrgId(100L, 1L)).thenReturn(List.of(file));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> integrationService.deleteIntegration(100L, 1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        // After files cleared, delete succeeds
        when(fileRepository.findByIntegrationIdAndOrganisationOrgId(100L, 1L)).thenReturn(List.of());
        assertDoesNotThrow(() -> integrationService.deleteIntegration(100L, 1L));
    }
}
