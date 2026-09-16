package com.taceiq;

import com.taceiq.entity.Organisation;
import com.taceiq.entity.Permission;
import com.taceiq.entity.Role;
import com.taceiq.entity.User;
import com.taceiq.repository.OrganisationRepository;
import com.taceiq.repository.PermissionRepository;
import com.taceiq.repository.RoleRepository;
import com.taceiq.repository.UserRepository;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.PermissionService;
import com.taceiq.service.RoleService;
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

public class RolePermissionManagementTest {

    @Mock RoleRepository roleRepository;
    @Mock PermissionRepository permissionRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock UserRepository userRepository;
    @Mock AuthorizationService authorizationService;

    RoleService roleService;
    PermissionService permissionService;

    Organisation orgA;
    Organisation orgB;
    Role adminRoleA;
    Role editorRoleA;
    Role adminRoleB;
    Permission perm1;
    Permission perm2;
    Permission perm3;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        roleService = new RoleService(roleRepository, permissionRepository, organisationRepository, authorizationService, userRepository);
        permissionService = new PermissionService(permissionRepository);
        orgA = Organisation.builder().orgId(1L).name("OrgA").build();
        orgB = Organisation.builder().orgId(2L).name("OrgB").build();
        perm1 = Permission.builder().id(1L).name("USER_CREATE").build();
        perm2 = Permission.builder().id(2L).name("USER_READ").build();
        perm3 = Permission.builder().id(3L).name("MANAGE_ROLES").build();
        adminRoleA = Role.builder().id(10L).name("ADMIN").organisation(orgA).description("admin").permissions(new HashSet<>(Set.of(perm1, perm2))).build();
        editorRoleA = Role.builder().id(11L).name("EDITOR").organisation(orgA).description("editor").permissions(new HashSet<>()).build();
        adminRoleB = Role.builder().id(20L).name("ADMIN").organisation(orgB).permissions(new HashSet<>()).build();

        // default allow escalation for tests that should succeed
        lenient().doNothing().when(authorizationService).requireCanAssignPermissions(any());
        lenient().when(roleRepository.findWithPermissionsByIdAndOrganisationOrgId(anyLong(), anyLong()))
                .thenAnswer(inv -> roleRepository.findByIdAndOrganisationOrgId(inv.getArgument(0), inv.getArgument(1)));
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

    // --- Role creation ---
    @Test
    void roleCreation_success() {
        Role newRole = Role.builder().name("MANAGER").description("manages").build();
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(roleRepository.existsByNameAndOrganisationOrgId("MANAGER", 1L)).thenReturn(false);
        when(roleRepository.save(any())).thenAnswer(i -> {
            Role r = i.getArgument(0);
            r.setId(100L);
            return r;
        });
        Role created = roleService.createRole(newRole, 1L);
        assertEquals("MANAGER", created.getName());
        assertEquals(1L, created.getOrganisation().getOrgId());
        assertTrue(created.getPermissions().isEmpty(), "Permissions should be cleared on create");
    }

    @Test
    void roleCreation_blankName_400() {
        Role newRole = Role.builder().name("   ").build();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> roleService.createRole(newRole, 1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void roleCreation_duplicateName_409() {
        Role newRole = Role.builder().name("EDITOR").build();
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(roleRepository.existsByNameAndOrganisationOrgId("EDITOR", 1L)).thenReturn(true);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> roleService.createRole(newRole, 1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void roleCreation_forcedOrganization() {
        Role newRole = Role.builder().name("TEST").organisation(orgB).build(); // client tries to set orgB
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(roleRepository.existsByNameAndOrganisationOrgId("TEST", 1L)).thenReturn(false);
        when(roleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Role created = roleService.createRole(newRole, 1L);
        assertEquals(1L, created.getOrganisation().getOrgId(), "Should be forced to current org, not client org");
    }

    // --- List/Get ---
    @Test
    void listRoles_onlyCurrentOrg() {
        when(roleRepository.findByOrganisationOrgId(1L)).thenReturn(List.of(adminRoleA, editorRoleA));
        List<Role> list = roleService.getRolesByOrgId(1L, 1L);
        assertEquals(2, list.size());
    }

    @Test
    void getRole_success() {
        when(roleRepository.findByIdAndOrganisationOrgId(10L, 1L)).thenReturn(Optional.of(adminRoleA));
        Role r = roleService.getRoleById(10L, 1L);
        assertEquals("ADMIN", r.getName());
    }

    @Test
    void getRole_crossOrg_403() {
        when(roleRepository.findByIdAndOrganisationOrgId(10L, 1L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> roleService.getRoleById(10L, 1L));
        // via service helper for by-name with org mismatch
        assertThrows(AccessDeniedException.class, () -> roleService.getRoleByNameAndOrgId("ADMIN", 2L, 1L));
        assertThrows(AccessDeniedException.class, () -> roleService.getRolesByOrgId(2L, 1L));
    }

    // --- Update ---
    @Test
    void roleUpdate_success() {
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(editorRoleA));
        when(roleRepository.existsByNameAndOrganisationOrgId("LEAD", 1L)).thenReturn(false);
        when(roleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Role updated = roleService.updateRole(11L, Role.builder().name("LEAD").description("lead desc").build(), 1L);
        assertEquals("LEAD", updated.getName());
        assertEquals("lead desc", updated.getDescription());
        assertEquals(1L, updated.getOrganisation().getOrgId(), "Org must not change");
    }

    @Test
    void roleUpdate_duplicateName_409() {
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(editorRoleA));
        when(roleRepository.existsByNameAndOrganisationOrgId("ADMIN", 1L)).thenReturn(true);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> roleService.updateRole(11L, Role.builder().name("ADMIN").build(), 1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void roleUpdate_organizationCannotChange() {
        Role existing = Role.builder().id(11L).name("EDITOR").organisation(orgA).description("old").permissions(new HashSet<>()).build();
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(existing));
        when(roleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Role malicious = Role.builder().name("EDITOR").organisation(orgB).description("new").build();
        Role updated = roleService.updateRole(11L, malicious, 1L);
        assertEquals(1L, updated.getOrganisation().getOrgId());
        assertNotEquals(2L, updated.getOrganisation().getOrgId());
    }

    @Test
    void roleUpdate_crossOrg_403() {
        when(roleRepository.findByIdAndOrganisationOrgId(20L, 1L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> roleService.updateRole(20L, Role.builder().name("HACK").build(), 1L));
    }

    // --- Delete ---
    @Test
    void roleDelete_success() {
        Role toDelete = Role.builder().id(11L).name("EDITOR").organisation(orgA).build();
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(toDelete));
        when(userRepository.findByRoleIdAndOrganisationOrgId(11L, 1L)).thenReturn(List.of());
        assertDoesNotThrow(() -> roleService.deleteRole(11L, 1L));
        verify(roleRepository).delete(toDelete);
    }

    @Test
    void roleDelete_protectedAdmin_403() {
        when(roleRepository.findByIdAndOrganisationOrgId(10L, 1L)).thenReturn(Optional.of(adminRoleA));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> roleService.deleteRole(10L, 1L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("ADMIN"));
    }

    @Test
    void roleDelete_assignedRole_409() {
        Role assigned = Role.builder().id(11L).name("EDITOR").organisation(orgA).build();
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(assigned));
        User u = User.builder().id(100L).username("alice").role(assigned).organisation(orgA).build();
        when(userRepository.findByRoleIdAndOrganisationOrgId(11L, 1L)).thenReturn(List.of(u));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> roleService.deleteRole(11L, 1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void roleDelete_crossOrg_403() {
        when(roleRepository.findByIdAndOrganisationOrgId(20L, 1L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> roleService.deleteRole(20L, 1L));
    }

    // --- Permission catalog ---
    @Test
    void permissionCatalog_retrieval() {
        when(permissionRepository.findAll()).thenReturn(List.of(perm1, perm2, perm3));
        List<Permission> all = permissionService.getAllPermissions();
        assertEquals(3, all.size());
        // get by id
        when(permissionRepository.findById(1L)).thenReturn(Optional.of(perm1));
        assertEquals("USER_CREATE", permissionService.getPermissionById(1L).getName());
        when(permissionRepository.findByName("USER_CREATE")).thenReturn(Optional.of(perm1));
        assertEquals(1L, permissionService.getPermissionByName("USER_CREATE").getId());
    }

    @Test
    void permissionCatalog_platformAdminAccess() {
        // Simulate platform admin: isPlatformAdmin true should bypass org check in controller
        // Here we test service directly - service has no org check, just returns catalog
        when(permissionRepository.findAll()).thenReturn(List.of(perm1));
        assertDoesNotThrow(() -> permissionService.getAllPermissions());
    }

    // --- Role permission retrieval ---
    @Test
    void rolePermissionRetrieval_success() {
        when(roleRepository.findByIdAndOrganisationOrgId(10L, 1L)).thenReturn(Optional.of(adminRoleA));
        Set<Permission> perms = roleService.getPermissionsForRole(10L, 1L);
        assertEquals(2, perms.size());
    }

    @Test
    void rolePermissionRetrieval_crossOrg_403() {
        when(roleRepository.findByIdAndOrganisationOrgId(20L, 1L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> roleService.getPermissionsForRole(20L, 1L));
    }

    // --- Permission assignment ---
    @Test
    void permissionAssignment_success() {
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(editorRoleA));
        when(permissionRepository.findAllById(Set.of(1L))).thenReturn(List.of(perm1));
        when(roleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Role updated = roleService.assignPermissionsToRole(11L, Set.of(1L), 1L);
        assertTrue(updated.getPermissions().contains(perm1));
        verify(permissionRepository).findAllById(Set.of(1L));
    }

    @Test
    void permissionAssignment_duplicate_idempotent() {
        Role roleWithPerm = Role.builder().id(11L).name("EDITOR").organisation(orgA).permissions(new HashSet<>(Set.of(perm1))).build();
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(roleWithPerm));
        when(permissionRepository.findAllById(Set.of(1L))).thenReturn(List.of(perm1));
        // should not duplicate, and return without extra save? Currently returns same role without save if already present
        Role result = roleService.assignPermissionsToRole(11L, Set.of(1L), 1L);
        assertEquals(1, result.getPermissions().size());
        // verify save not called if duplicate? In our implementation we return early without save
        // So verify no extra save or single save - we allow either
    }

    @Test
    void permissionAssignment_unauthorized_403() {
        doThrow(new AccessDeniedException("Cannot grant permission 'USER_CREATE' you do not have"))
                .when(authorizationService).requireCanAssignPermissions(any());
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(editorRoleA));
        when(permissionRepository.findAllById(Set.of(1L))).thenReturn(List.of(perm1));
        assertThrows(AccessDeniedException.class, () -> roleService.assignPermissionsToRole(11L, Set.of(1L), 1L));
    }

    @Test
    void permissionAssignment_privilegeEscalation_403() {
        // same as unauthorized - user tries to grant perm they don't have
        doThrow(new AccessDeniedException("Cannot grant permission")).when(authorizationService).requireCanAssignPermissions(any());
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(editorRoleA));
        when(permissionRepository.findAllById(Set.of(3L))).thenReturn(List.of(perm3));
        assertThrows(AccessDeniedException.class, () -> roleService.assignPermissionsToRole(11L, Set.of(3L), 1L));
    }

    @Test
    void permissionAssignment_crossOrg_403() {
        when(roleRepository.findByIdAndOrganisationOrgId(20L, 1L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> roleService.assignPermissionsToRole(20L, Set.of(1L), 1L));
    }

    @Test
    void permissionAssignment_notFound_404() {
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(editorRoleA));
        when(permissionRepository.findAllById(Set.of(999L))).thenReturn(List.of());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> roleService.assignPermissionsToRole(11L, Set.of(999L), 1L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // --- Permission removal ---
    @Test
    void permissionRemoval_success() {
        Role roleWithPerms = Role.builder().id(11L).name("EDITOR").organisation(orgA).permissions(new HashSet<>(Set.of(perm1, perm2))).build();
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(roleWithPerms));
        when(roleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Role updated = roleService.removePermissionsFromRole(11L, Set.of(1L), 1L);
        assertFalse(updated.getPermissions().contains(perm1));
        assertTrue(updated.getPermissions().contains(perm2));
    }

    @Test
    void permissionRemoval_crossOrg_403() {
        when(roleRepository.findByIdAndOrganisationOrgId(20L, 1L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> roleService.removePermissionsFromRole(20L, Set.of(1L), 1L));
    }

    // --- Platform admin unchanged ---
    @Test
    void platformAdmin_canAccessPermissionCatalog() {
        // PermissionController allows platform admin without org check
        // Simulate isPlatformAdmin true
        AuthorizationService realAuth = new AuthorizationService(userRepository);
        setAuth("admin@taceiq.com", "PLATFORM_ADMIN");
        assertTrue(realAuth.isPlatformAdmin());
        // should not throw when checking isPlatformAdmin bypass
    }

    @Test
    void platformAdmin_cannotAccessTenantRole() {
        // Tenant isolation still applies - even platform admin should get 403 for tenant role via org-scoped query
        // But RoleController for tenant roles explicitly throws for platform admin in getCurrentOrgId
        // We test via real AuthorizationService
        AuthorizationService realAuth = new AuthorizationService(userRepository);
        setAuth("admin@taceiq.com", "PLATFORM_ADMIN");
        assertThrows(AccessDeniedException.class, () -> realAuth.getCurrentOrgId());
    }
}
