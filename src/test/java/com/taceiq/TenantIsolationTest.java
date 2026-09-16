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

public class TenantIsolationTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PermissionRepository permissionRepository;
    @Mock FileRepository fileRepository;
    @Mock IntegrationRepository integrationRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock CanonicalEvidenceRepository canonicalEvidenceRepository;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock com.taceiq.security.AuthorizationService authorizationService;

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
    File fileB;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        // AuthorizationService is mocked - allow role assignments without escalation check for these isolation tests
        doNothing().when(authorizationService).requireCanAssignRole(any());
        doNothing().when(authorizationService).requireCanAssignPermissions(any());
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

    // --- Users ---
    @Test
    void userGetById_sameOrg_ok() {
        when(userRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(userA));
        User u = userService.getUserById(100L, 1L);
        assertEquals("alice", u.getUsername());
    }

    @Test
    void userGetById_crossOrg_denied() {
        when(userRepository.findByIdAndOrganisationOrgId(200L, 1L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> userService.getUserById(200L, 1L));
    }

    @Test
    void userGetByUsername_sameOrg_ok() {
        when(userRepository.findByUsernameAndOrganisationOrgId("alice", 1L)).thenReturn(Optional.of(userA));
        User u = userService.getUserByUsername("alice", 1L);
        assertEquals(100L, u.getId());
    }

    @Test
    void userGetByUsername_crossOrg_denied() {
        when(userRepository.findByUsernameAndOrganisationOrgId("bob", 1L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> userService.getUserByUsername("bob", 1L));
    }

    @Test
    void userList_crossOrg_denied() {
        assertThrows(AccessDeniedException.class, () -> userService.getUsersByOrg(2L, 1L));
    }

    @Test
    void userList_sameOrg_ok() {
        when(userRepository.findByOrganisationOrgId(1L)).thenReturn(List.of(userA));
        List<User> list = userService.getUsersByOrg(1L, 1L);
        assertEquals(1, list.size());
    }

    @Test
    void userCreate_forcesOrg() {
        User newUser = User.builder().username("charlie").password("Secret123!").role(roleA).build();
        when(userRepository.existsByUsername("charlie")).thenReturn(false);
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(roleRepository.findByIdAndOrganisationOrgId(10L, 1L)).thenReturn(Optional.of(roleA));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        User created = userService.createUser(newUser, 1L);
        assertEquals(1L, created.getOrganisation().getOrgId());
    }

    @Test
    void userCreate_roleFromOtherOrg_denied() {
        User newUser = User.builder().username("eve").password("Secret123!").role(roleB).build();
        when(userRepository.existsByUsername("eve")).thenReturn(false);
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(roleRepository.findByIdAndOrganisationOrgId(20L, 1L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> userService.createUser(newUser, 1L));
    }

    // --- Roles ---
    @Test
    void roleGetById_sameOrg_ok() {
        when(roleRepository.findByIdAndOrganisationOrgId(10L, 1L)).thenReturn(Optional.of(roleA));
        Role r = roleService.getRoleById(10L, 1L);
        assertEquals("ADMIN", r.getName());
    }

    @Test
    void roleGetById_crossOrg_denied() {
        when(roleRepository.findByIdAndOrganisationOrgId(20L, 1L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> roleService.getRoleById(20L, 1L));
    }

    @Test
    void roleCreate_forcesOrg() {
        Role newRole = Role.builder().name("EDITOR").description("edit").build();
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(roleRepository.existsByNameAndOrganisationOrgId("EDITOR", 1L)).thenReturn(false);
        when(roleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Role created = roleService.createRole(newRole, 1L);
        assertEquals(1L, created.getOrganisation().getOrgId());
    }

    @Test
    void roleUpdate_crossOrg_denied() {
        when(roleRepository.findByIdAndOrganisationOrgId(20L, 1L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> roleService.updateRole(20L, Role.builder().name("HACK").build(), 1L));
    }

    @Test
    void roleDelete_crossOrg_denied() {
        when(roleRepository.findByIdAndOrganisationOrgId(20L, 1L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> roleService.deleteRole(20L, 1L));
    }

    @Test
    void roleAssignPermissions_crossOrg_denied() {
        when(roleRepository.findByIdAndOrganisationOrgId(20L, 1L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> roleService.assignPermissionsToRole(20L, Set.of(1L), 1L));
    }

    @Test
    void roleGetPermissions_crossOrg_denied() {
        when(roleRepository.findByIdAndOrganisationOrgId(20L, 1L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> roleService.getPermissionsForRole(20L, 1L));
    }

    @Test
    void roleList_crossOrg_denied() {
        assertThrows(AccessDeniedException.class, () -> roleService.getRolesByOrgId(2L, 1L));
    }

    // --- Files ---
    @Test
    void fileGetById_sameOrg_ok() {
        File f = File.builder().id(1000L).organisation(orgA).sourceType("MANUAL_UPLOAD").build();
        when(fileRepository.findByIdAndOrganisationOrgId(1000L, 1L)).thenReturn(Optional.of(f));
        when(fileRepository.findById(1000L)).thenReturn(Optional.of(f));
        File got = fileService.getFileById(1000L, 1L);
        assertEquals(1000L, got.getId());
    }

    @Test
    void fileGetById_crossOrg_denied() {
        when(fileRepository.findByIdAndOrganisationOrgId(2000L, 1L)).thenReturn(Optional.empty());
        when(fileRepository.findById(2000L)).thenReturn(Optional.of(fileB));
        assertThrows(AccessDeniedException.class, () -> fileService.getFileById(2000L, 1L));
    }

    @Test
    void fileList_crossOrg_denied() {
        assertThrows(AccessDeniedException.class, () -> fileService.getFilesByOrganisation(2L, 1L));
    }

    @Test
    void fileList_sameOrg_ok() {
        File f = File.builder().id(1L).organisation(orgA).sourceType("MANUAL_UPLOAD").build();
        when(fileRepository.findByOrganisationOrgId(1L)).thenReturn(List.of(f));
        List<File> list = fileService.getFilesByOrganisation(1L, 1L);
        assertEquals(1, list.size());
    }

    @Test
    void fileGetByIntegration_sameOrg_ok() {
        Integration integA = Integration.builder().id(500L).organisation(orgA).name("intA").build();
        File f = File.builder().id(1000L).organisation(orgA).integration(integA).sourceType("INTEGRATION").build();
        when(integrationRepository.findById(500L)).thenReturn(Optional.of(integA));
        when(fileRepository.findByIntegrationIdAndOrganisationOrgId(500L, 1L)).thenReturn(List.of(f));
        List<File> list = fileService.getFilesByIntegration(500L, 1L);
        assertEquals(1, list.size());
    }

    @Test
    void fileGetByIntegration_crossOrg_denied() {
        Integration integB = Integration.builder().id(600L).organisation(orgB).name("intB").build();
        when(integrationRepository.findById(600L)).thenReturn(Optional.of(integB));
        assertThrows(AccessDeniedException.class, () -> fileService.getFilesByIntegration(600L, 1L));
    }

    @Test
    void fileUpdate_crossOrg_denied() {
        when(fileRepository.findByIdAndOrganisationOrgId(2000L, 1L)).thenReturn(Optional.empty());
        when(fileRepository.findById(2000L)).thenReturn(Optional.of(fileB));
        assertThrows(AccessDeniedException.class, () -> fileService.updateFile(2000L, File.builder().originalName("x").build(), 1L));
    }

    @Test
    void fileDelete_crossOrg_denied() {
        when(fileRepository.findByIdAndOrganisationOrgId(2000L, 1L)).thenReturn(Optional.empty());
        when(fileRepository.findById(2000L)).thenReturn(Optional.of(fileB));
        assertThrows(AccessDeniedException.class, () -> fileService.deleteFile(2000L, 1L));
    }

    @Test
    void fileCreate_forcesOrg() {
        File newFile = File.builder().sourceType("MANUAL_UPLOAD").originalName("a.pdf").build();
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(fileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        File created = fileService.createFile(newFile, 1L);
        assertEquals(1L, created.getOrganisation().getOrgId());
    }

    @Test
    void fileCreate_integrationCrossOrg_denied() {
        Integration integB = Integration.builder().id(600L).organisation(orgB).build();
        File newFile = File.builder().sourceType("INTEGRATION").integration(integB).originalName("a.pdf").build();
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(integrationRepository.findById(600L)).thenReturn(Optional.of(integB));
        assertThrows(AccessDeniedException.class, () -> fileService.createFile(newFile, 1L));
    }
}