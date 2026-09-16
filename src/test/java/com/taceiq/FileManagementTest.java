package com.taceiq;

import com.taceiq.entity.*;
import com.taceiq.repository.*;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class FileManagementTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PermissionRepository permissionRepository;
    @Mock FileRepository fileRepository;
    @Mock IntegrationRepository integrationRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock CanonicalEvidenceRepository canonicalEvidenceRepository;
    @Mock com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;

    AuthorizationService authorizationService;
    UserService userService;
    RoleService roleService;
    FileService fileService;

    Organisation orgA;
    Organisation orgB;
    Role roleA;
    Role roleB;
    User userA;
    User userB;
    Integration integA;
    Integration integB;
    File fileA;
    File fileB; // file belonging to orgB for cross-org tests

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        authorizationService = new AuthorizationService(userRepository);
        userService = new UserService(userRepository, organisationRepository, roleRepository, new BCryptPasswordEncoder(), authorizationService);
        roleService = new RoleService(roleRepository, permissionRepository, organisationRepository, authorizationService, userRepository);
        fileService = new FileService(fileRepository, organisationRepository, integrationRepository, canonicalEvidenceRepository, new com.fasterxml.jackson.databind.ObjectMapper(), eventPublisher);

        orgA = Organisation.builder().orgId(1L).name("OrgA").build();
        orgB = Organisation.builder().orgId(2L).name("OrgB").build();
        roleA = Role.builder().id(10L).name("ADMIN").organisation(orgA).build();
        roleB = Role.builder().id(20L).name("ADMIN").organisation(orgB).build();
        userA = User.builder().id(100L).username("alice").password("pass").organisation(orgA).role(roleA).build();
        userB = User.builder().id(200L).username("bob").password("pass").organisation(orgB).role(roleB).build();

        integA = Integration.builder().id(500L).organisation(orgA).name("intA").build();
        integB = Integration.builder().id(600L).organisation(orgB).name("intB").build();
        fileA = File.builder().id(1000L).organisation(orgA).sourceType("MANUAL_UPLOAD").build();
        fileB = File.builder().id(2000L).organisation(orgB).sourceType("MANUAL_UPLOAD").build();
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

    // --- Create/Upload ---

    @Test
    void createFile_forcesOrg() {
        File newFile = File.builder().sourceType("MANUAL_UPLOAD").originalName("a.pdf").build();
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(fileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        File created = fileService.createFile(newFile, 1L);
        assertEquals(1L, created.getOrganisation().getOrgId());
    }

    @Test
    void createFile_integrationOwnershipVerified() {
        File newFile = File.builder().sourceType("INTEGRATION").integration(integA).originalName("a.pdf").build();
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(integrationRepository.findById(500L)).thenReturn(Optional.of(integA));
        when(fileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        File created = fileService.createFile(newFile, 1L);
        assertEquals(integA, created.getIntegration());
    }

    @Test
    void createFile_integrationCrossOrg_denied() {
        File newFile = File.builder().sourceType("INTEGRATION").integration(integB).originalName("a.pdf").build();
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(integrationRepository.findById(600L)).thenReturn(Optional.of(integB));
        assertThrows(AccessDeniedException.class, () -> fileService.createFile(newFile, 1L));
    }

    @Test
    void createFile_missingSourceType_400() {
        File newFile = File.builder().originalName("a.pdf").build();
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(fileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThrows(ResponseStatusException.class, () -> fileService.createFile(newFile, 1L));
        var ex = assertThrows(ResponseStatusException.class, () -> fileService.createFile(newFile, 1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createFile_missingOriginalName_400() {
        File newFile = File.builder().sourceType("MANUAL_UPLOAD").build();
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(fileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThrows(ResponseStatusException.class, () -> fileService.createFile(newFile, 1L));
        var ex = assertThrows(ResponseStatusException.class, () -> fileService.createFile(newFile, 1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    // --- List/Get ---

    @Test
    void listFiles_sameOrg_ok() {
        when(fileRepository.findByOrganisationOrgId(1L)).thenReturn(List.of(fileA));
        List<File> list = fileService.getFilesByOrganisation(1L, 1L);
        assertEquals(1, list.size());
    }

    @Test
    void listFiles_crossOrg_denied() {
        assertThrows(AccessDeniedException.class, () -> fileService.getFilesByOrganisation(2L, 1L));
    }

    @Test
    void getFileById_sameOrg_ok() {
        when(fileRepository.findById(1000L)).thenReturn(Optional.of(fileA));
        when(fileRepository.findByIdAndOrganisationOrgId(1000L, 1L)).thenReturn(Optional.of(fileA));
        File got = fileService.getFileById(1000L, 1L);
        assertEquals(1000L, got.getId());
    }

    @Test
    void getFileById_notFound_404() {
        when(fileRepository.findById(9999L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> fileService.getFileById(9999L, 1L));
        var ex = assertThrows(ResponseStatusException.class, () -> fileService.getFileById(9999L, 1L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getFileById_crossOrg_denied() {
        // File 2000L exists but belongs to orgB (orgId=2), not orgA (orgId=1)
        when(fileRepository.findById(2000L)).thenReturn(Optional.of(fileB));
        when(fileRepository.findByIdAndOrganisationOrgId(2000L, 1L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> fileService.getFileById(2000L, 1L));
    }

    @Test
    void getFilesByIntegration_sameOrg_ok() {
        when(integrationRepository.findById(500L)).thenReturn(Optional.of(integA));
        when(fileRepository.findByIntegrationIdAndOrganisationOrgId(500L, 1L)).thenReturn(List.of(fileA));
        List<File> list = fileService.getFilesByIntegration(500L, 1L);
        assertEquals(1, list.size());
    }

    @Test
    void getFilesByIntegration_crossOrg_denied() {
        when(integrationRepository.findById(600L)).thenReturn(Optional.of(integB));
        assertThrows(AccessDeniedException.class, () -> fileService.getFilesByIntegration(600L, 1L));
    }

    @Test
    void getFilesByIntegration_notFound_404() {
        when(integrationRepository.findById(9999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> fileService.getFilesByIntegration(9999L, 1L));
    }

    // --- Update Metadata ---

    @Test
    void updateFileMetadata_sameOrg_ok() {
        when(fileRepository.findById(1000L)).thenReturn(Optional.of(fileA));
        when(fileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        File updated = fileService.updateFileMetadata(1000L, fileA, 1L);
        assertNotNull(updated);
    }

    @Test
    void updateFileMetadata_crossOrg_denied() {
        // File 2000L exists but belongs to orgB (orgId=2), not orgA (orgId=1)
        when(fileRepository.findById(2000L)).thenReturn(Optional.of(fileB));
        assertThrows(AccessDeniedException.class, () -> fileService.updateFileMetadata(2000L, fileB, 1L));
    }

    @Test
    void updateFileMetadata_missingFields_ignored() {
        File partial = File.builder().build();
        when(fileRepository.findById(1000L)).thenReturn(Optional.of(fileA));
        when(fileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        File updated = fileService.updateFileMetadata(1000L, partial, 1L);
        assertNotNull(updated);
    }

    @Test
    void updateFileStatus_validStatus_ok() {
        when(fileRepository.findById(1000L)).thenReturn(Optional.of(fileA));
        when(fileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        File updated = fileService.updateFileStatus(1000L, "UPLOADED", 1L);
        assertEquals("UPLOADED", updated.getStatus());
    }

    @Test
    void updateFileStatus_invalidStatus_400() {
        when(fileRepository.findById(1000L)).thenReturn(Optional.of(fileA));
        assertThrows(ResponseStatusException.class, () -> fileService.updateFileStatus(1000L, "INVALID_STATUS", 1L));
        var ex = assertThrows(ResponseStatusException.class, () -> fileService.updateFileStatus(1000L, "INVALID_STATUS", 1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateFileStatus_crossOrg_denied() {
        // File 2000L exists but belongs to orgB (orgId=2), not orgA (orgId=1)
        when(fileRepository.findById(2000L)).thenReturn(Optional.of(fileB));
        assertThrows(AccessDeniedException.class, () -> fileService.updateFileStatus(2000L, "UPLOADED", 1L));
    }

    // --- Update Full ---

    @Test
    void updateFile_full_ok() {
        when(fileRepository.findById(1000L)).thenReturn(Optional.of(fileA));
        when(fileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        File updated = fileService.updateFile(1000L, fileA, 1L);
        assertNotNull(updated);
    }

    @Test
    void updateFile_crossOrg_denied() {
        // File 2000L exists but belongs to orgB (orgId=2), not orgA (orgId=1)
        when(fileRepository.findById(2000L)).thenReturn(Optional.of(fileB));
        assertThrows(AccessDeniedException.class, () -> fileService.updateFile(2000L, fileB, 1L));
    }

    // --- Delete ---

    @Test
    void deleteFile_sameOrg_ok() {
        when(fileRepository.findById(1000L)).thenReturn(Optional.of(fileA));
        doNothing().when(fileRepository).delete(any());
        fileService.deleteFile(1000L, 1L);
        verify(fileRepository).delete(any());
    }

    @Test
    void deleteFile_crossOrg_denied() {
        // File 2000L exists but belongs to orgB (orgId=2), not orgA (orgId=1)
        when(fileRepository.findById(2000L)).thenReturn(Optional.of(fileB));
        assertThrows(AccessDeniedException.class, () -> fileService.deleteFile(2000L, 1L));
    }

    @Test
    void deleteFile_isLastRefForIntegration_blocked() {
        fileA = File.builder().id(1000L).organisation(orgA).integration(integA).sourceType("MANUAL_UPLOAD").build();
        when(fileRepository.findById(1000L)).thenReturn(Optional.of(fileA));
        when(fileRepository.countByIntegrationIdAndOrganisationOrgId(500L, 1L)).thenReturn(1L);
        assertThrows(ResponseStatusException.class, () -> fileService.deleteFile(1000L, 1L));
        var ex = assertThrows(ResponseStatusException.class, () -> fileService.deleteFile(1000L, 1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void deleteFile_notLastRefForIntegration_allowed() {
        fileA = File.builder().id(1000L).organisation(orgA).integration(integA).sourceType("MANUAL_UPLOAD").build();
        when(fileRepository.findById(1000L)).thenReturn(Optional.of(fileA));
        when(fileRepository.countByIntegrationIdAndOrganisationOrgId(500L, 1L)).thenReturn(2L);
        fileService.deleteFile(1000L, 1L);
        verify(fileRepository).delete(any());
    }

    @Test
    void deleteFile_notFound_404() {
        when(fileRepository.findById(9999L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> fileService.deleteFile(9999L, 1L));
        var ex = assertThrows(ResponseStatusException.class, () -> fileService.deleteFile(9999L, 1L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // --- Platform Admin isolation ---

    @Test
    void platformAdmin_orgIdThrows() {
        setAuth("admin@taceiq.com", "PLATFORM_ADMIN");
        when(userRepository.findByUsername("admin@taceiq.com")).thenReturn(Optional.of(userA));
        assertThrows(AccessDeniedException.class, () -> authorizationService.getCurrentOrgId());
    }

    // --- Integration/File relationship ---

    @Test
    void fileIntegrationBelongsToOrg() {
        when(integrationRepository.findById(500L)).thenReturn(Optional.of(integA));
        when(fileRepository.findByIntegrationIdAndOrganisationOrgId(500L, 1L)).thenReturn(List.of(fileA));
        List<File> list = fileService.getFilesByIntegration(500L, 1L);
        assertEquals(1, list.size());
        assertEquals(orgA, list.get(0).getOrganisation());
    }

    @Test
    void fileIntegrationCrossOrgBlocked() {
        when(integrationRepository.findById(600L)).thenReturn(Optional.of(integB));
        assertThrows(AccessDeniedException.class, () -> fileService.getFilesByIntegration(600L, 1L));
    }
}