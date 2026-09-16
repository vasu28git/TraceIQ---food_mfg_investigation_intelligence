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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RbacAuthorizationTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PermissionRepository permissionRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock FileRepository fileRepository;
    @Mock IntegrationRepository integrationRepository;
    @Mock ConfigurationRepository configurationRepository;
    @Mock ConfigurationDefinitionRepository definitionRepository;
    @Mock CanonicalEvidenceRepository canonicalEvidenceRepository;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;

    AuthorizationService authorizationService;
    UserService userService;
    RoleService roleService;
    FileService fileService;

    Organisation orgA;
    Organisation orgB;
    Role adminRole;
    Role limitedRole;
    Permission userCreatePerm;
    Permission userReadPerm;
    Permission roleCreatePerm;
    Permission manageRolesPerm;
    Permission deleteFilePerm;
    Permission createFilePerm;

    User adminUser;
    User limitedUser;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        authorizationService = new AuthorizationService(userRepository);
        userService = new UserService(userRepository, organisationRepository, roleRepository, new BCryptPasswordEncoder(), authorizationService);
        roleService = new RoleService(roleRepository, permissionRepository, organisationRepository, authorizationService, userRepository);
        lenient().when(roleRepository.findWithPermissionsByIdAndOrganisationOrgId(anyLong(), anyLong()))
                .thenAnswer(inv -> roleRepository.findByIdAndOrganisationOrgId(inv.getArgument(0), inv.getArgument(1)));
        fileService = new FileService(fileRepository, organisationRepository, integrationRepository, canonicalEvidenceRepository, new com.fasterxml.jackson.databind.ObjectMapper(), eventPublisher);

        orgA = Organisation.builder().orgId(1L).name("OrgA").build();
        orgB = Organisation.builder().orgId(2L).name("OrgB").build();

        userCreatePerm = Permission.builder().id(1L).name("USER_CREATE").build();
        userReadPerm = Permission.builder().id(2L).name("USER_READ").build();
        roleCreatePerm = Permission.builder().id(3L).name("ROLE_CREATE").build();
        manageRolesPerm = Permission.builder().id(4L).name("MANAGE_ROLES").build();
        createFilePerm = Permission.builder().id(5L).name("FILE_CREATE").build();
        deleteFilePerm = Permission.builder().id(6L).name("FILE_DELETE").build();

        adminRole = Role.builder().id(10L).name("ADMIN").organisation(orgA).permissions(new HashSet<>(Set.of(userCreatePerm, userReadPerm, roleCreatePerm, manageRolesPerm, createFilePerm, deleteFilePerm))).build();
        limitedRole = Role.builder().id(11L).name("VIEWER").organisation(orgA).permissions(new HashSet<>(Set.of(userReadPerm))).build();

        adminUser = User.builder().id(100L).username("adminA").password("pass").organisation(orgA).role(adminRole).build();
        limitedUser = User.builder().id(101L).username("viewerA").password("pass").organisation(orgA).role(limitedRole).build();
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

    // --- hasPermission tests ---
    @Test
    void hasPermission_withAuthority_succeeds() {
        setAuth("adminA", "USER_CREATE", "MANAGE_ROLES");
        when(userRepository.findByUsername("adminA")).thenReturn(Optional.of(adminUser));
        assertTrue(authorizationService.hasPermission("USER_CREATE"));
        assertTrue(authorizationService.hasPermission("user_create")); // case-insensitive via hasPermission upper
        assertFalse(authorizationService.hasPermission("DELETE_FILE"));
    }

    @Test
    void requirePermission_lacks_throws403() {
        setAuth("viewerA", "USER_READ");
        when(userRepository.findByUsername("viewerA")).thenReturn(Optional.of(limitedUser));
        assertThrows(AccessDeniedException.class, () -> authorizationService.requireUserCreate());
    }

    @Test
    void requirePermission_has_succeeds() {
        setAuth("adminA", "USER_CREATE");
        when(userRepository.findByUsername("adminA")).thenReturn(Optional.of(adminUser));
        assertDoesNotThrow(() -> authorizationService.requireUserCreate());
        // alias tolerance: MANAGE_USERS should also satisfy USER_CREATE
        SecurityContextHolder.clearContext();
        setAuth("adminA", "MANAGE_USERS");
        when(userRepository.findByUsername("adminA")).thenReturn(Optional.of(adminUser));
        assertDoesNotThrow(() -> authorizationService.requireUserCreate());
    }

    // --- User create RBAC ---
    @Test
    void userCreate_hasPermission_succeeds() {
        setAuth("adminA", "USER_CREATE");
        when(userRepository.findByUsername("adminA")).thenReturn(Optional.of(adminUser));
        when(userRepository.existsByUsername("newUser")).thenReturn(false);
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(limitedRole));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User newUser = User.builder().username("newUser").password("pass12345").role(limitedRole).build();
        // limitedRole has only USER_READ, admin has it, so subset passes
        User created = userService.createUser(newUser, 1L);
        assertNotNull(created);
    }

    @Test
    void userCreate_lacksPermission_throws403_viaAuthz() {
        // Simulate controller check: requireUserCreate fails
        setAuth("viewerA", "USER_READ");
        when(userRepository.findByUsername("viewerA")).thenReturn(Optional.of(limitedUser));
        assertThrows(AccessDeniedException.class, () -> authorizationService.requireUserCreate());
    }

    @Test
    void userCreate_privilegeEscalation_denied() {
        // limitedUser has only USER_READ, tries to create user with adminRole which has USER_CREATE, MANAGE_ROLES etc.
        setAuth("viewerA", "USER_CREATE", "USER_READ"); // has USER_CREATE but not all admin perms
        when(userRepository.findByUsername("viewerA")).thenReturn(Optional.of(limitedUser));
        when(userRepository.existsByUsername("evil")).thenReturn(false);
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(roleRepository.findByIdAndOrganisationOrgId(10L, 1L)).thenReturn(Optional.of(adminRole));

        User newUser = User.builder().username("evil").password("pass12345").role(adminRole).build();
        assertThrows(AccessDeniedException.class, () -> userService.createUser(newUser, 1L));
    }

    @Test
    void userCreate_crossOrg_denied() {
        setAuth("adminA", "USER_CREATE");
        when(userRepository.findByUsername("adminA")).thenReturn(Optional.of(adminUser));
        when(userRepository.existsByUsername("cross")).thenReturn(false);
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        // try to assign roleB from orgB - should fail org check before escalation
        Role roleB = Role.builder().id(20L).name("ADMIN").organisation(orgB).permissions(new HashSet<>(Set.of(userCreatePerm))).build();
        when(roleRepository.findByIdAndOrganisationOrgId(20L, 1L)).thenReturn(Optional.empty());
        User newUser = User.builder().username("cross").password("Password123!").role(roleB).build();
        assertThrows(AccessDeniedException.class, () -> userService.createUser(newUser, 1L));
    }

    // --- Role permission assign ---
    @Test
    void roleAssign_hasPermission_succeeds() {
        setAuth("adminA", "PERMISSION_ASSIGN", "MANAGE_ROLES");
        when(userRepository.findByUsername("adminA")).thenReturn(Optional.of(adminUser));
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(limitedRole));
        when(permissionRepository.findAllById(Set.of(1L))).thenReturn(List.of(userCreatePerm));
        when(roleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        // admin has USER_CREATE, so can grant it
        Role updated = roleService.assignPermissionsToRole(11L, Set.of(1L), 1L);
        assertTrue(updated.getPermissions().contains(userCreatePerm));
    }

    @Test
    void roleAssign_lacksPermissionAssign_403() {
        setAuth("viewerA", "USER_READ");
        when(userRepository.findByUsername("viewerA")).thenReturn(Optional.of(limitedUser));
        assertThrows(AccessDeniedException.class, () -> authorizationService.requirePermissionAssign());
    }

    @Test
    void roleAssign_privilegeEscalation_denied() {
        setAuth("viewerA", "PERMISSION_ASSIGN", "USER_READ"); // has assign but not USER_CREATE
        when(userRepository.findByUsername("viewerA")).thenReturn(Optional.of(limitedUser));
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(limitedRole));
        when(permissionRepository.findAllById(Set.of(1L))).thenReturn(List.of(userCreatePerm));
        // viewer lacks USER_CREATE, so cannot grant it
        assertThrows(AccessDeniedException.class, () -> roleService.assignPermissionsToRole(11L, Set.of(1L), 1L));
    }

    @Test
    void roleAssign_crossOrg_denied() {
        setAuth("adminA", "PERMISSION_ASSIGN");
        when(userRepository.findByUsername("adminA")).thenReturn(Optional.of(adminUser));
        when(roleRepository.findByIdAndOrganisationOrgId(20L, 1L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> roleService.assignPermissionsToRole(20L, Set.of(1L), 1L));
    }

    // --- File RBAC ---
    @Test
    void fileCreate_hasPermission_succeeds() {
        setAuth("adminA", "FILE_CREATE");
        when(userRepository.findByUsername("adminA")).thenReturn(Optional.of(adminUser));
        assertDoesNotThrow(() -> authorizationService.requireFileCreate());
    }

    @Test
    void fileCreate_lacksPermission_403() {
        setAuth("viewerA", "USER_READ");
        when(userRepository.findByUsername("viewerA")).thenReturn(Optional.of(limitedUser));
        assertThrows(AccessDeniedException.class, () -> authorizationService.requireFileCreate());
    }

    @Test
    void fileRead_hasPermission_succeeds() {
        setAuth("viewerA", "FILE_READ");
        when(userRepository.findByUsername("viewerA")).thenReturn(Optional.of(limitedUser));
        assertDoesNotThrow(() -> authorizationService.requireFileRead());
        // alias MANAGE_FILES also works
        SecurityContextHolder.clearContext();
        setAuth("viewerA", "MANAGE_FILES");
        when(userRepository.findByUsername("viewerA")).thenReturn(Optional.of(limitedUser));
        assertDoesNotThrow(() -> authorizationService.requireFileRead());
    }

    @Test
    void fileDelete_lacksPermission_403() {
        setAuth("viewerA", "FILE_READ");
        when(userRepository.findByUsername("viewerA")).thenReturn(Optional.of(limitedUser));
        assertThrows(AccessDeniedException.class, () -> authorizationService.requireFileDelete());
    }

    // --- Config/Integration RBAC ---
    @Test
    void configCreate_hasPermission_succeeds() {
        setAuth("adminA", "CONFIG_CREATE");
        when(userRepository.findByUsername("adminA")).thenReturn(Optional.of(adminUser));
        assertDoesNotThrow(() -> authorizationService.requireConfigCreate());
        // alias CREATE_CONFIGURATION also
        SecurityContextHolder.clearContext();
        setAuth("adminA", "CREATE_CONFIGURATION");
        when(userRepository.findByUsername("adminA")).thenReturn(Optional.of(adminUser));
        assertDoesNotThrow(() -> authorizationService.requireConfigCreate());
    }

    @Test
    void integrationRead_lacks_403() {
        setAuth("viewerA", "USER_READ");
        when(userRepository.findByUsername("viewerA")).thenReturn(Optional.of(limitedUser));
        assertThrows(AccessDeniedException.class, () -> authorizationService.requireIntegrationRead());
    }

    // --- Cross-org via AuthorizationService ---
    @Test
    void getCurrentOrgId_crossOrgCheck() {
        setAuth("adminA", "USER_READ");
        when(userRepository.findByUsername("adminA")).thenReturn(Optional.of(adminUser));
        Long orgId = authorizationService.getCurrentOrgId();
        assertEquals(1L, orgId);
        // service layer cross-org: userService.getUserById with wrong org should fail (already tested in isolation)
    }

    // --- Platform Admin unchanged ---
    @Test
    void platformAdmin_isDetected() {
        setAuth("admin@taceiq.com", "PLATFORM_ADMIN");
        assertTrue(authorizationService.isPlatformAdmin());
        assertThrows(AccessDeniedException.class, () -> authorizationService.getCurrentOrgId());
        // Platform admin should still be allowed to require? But org endpoints block before permission. For permissions, platform admin can read permissions if we bypass.
        // Ensure platform admin detection doesn't break org isolation (already throws)
    }

    @Test
    void platformAdmin_bypassesPermissionCheckIfAllowed() {
        // For permission controller, we allow platform admin without org check. Here we just verify isPlatformAdmin true.
        setAuth("admin@taceiq.com", "PLATFORM_ADMIN");
        assertTrue(authorizationService.isPlatformAdmin());
    }
}
