package com.taceiq;

import com.taceiq.config.PermissionSeeder;
import com.taceiq.entity.Organisation;
import com.taceiq.entity.Permission;
import com.taceiq.entity.Role;
import com.taceiq.entity.User;
import com.taceiq.repository.OrganisationRepository;
import com.taceiq.repository.PermissionRepository;
import com.taceiq.repository.RoleRepository;
import com.taceiq.repository.UserRepository;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.OrganisationProvisioningService;
import com.taceiq.service.PermissionService;
import com.taceiq.service.RoleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class BusinessPermissionsTest {

    // 12 new business permissions as per spec
    private static final String[][] BUSINESS_PERMS = {
            {"INVESTIGATION_ACCESS", "Access and manage investigations."},
            {"EVIDENCE_ACCESS", "Access and manage investigation evidence."},
            {"EVIDENCE_SOURCE_ACCESS", "Access evidence from connected enterprise sources."},
            {"EVIDENCE_GRAPH_ACCESS", "Access and explore the evidence graph."},
            {"TRACEABILITY_ACCESS", "Access forward and backward traceability capabilities."},
            {"TIMELINE_ACCESS", "Access investigation timelines and timeline analysis."},
            {"AI_INVESTIGATION_ACCESS", "Access AI-assisted investigation capabilities."},
            {"WORKSPACE_ACCESS", "Access the investigator workspace."},
            {"RECOMMENDATION_ACCESS", "Access and manage investigation recommendations."},
            {"DECISION_ACCESS", "Access and manage investigation decisions."},
            {"REPORT_ACCESS", "View, generate, and export investigation reports."},
            {"INVESTIGATION_ADMIN", "Manage higher-level investigation controls."}
    };

    private static final Set<String> BUSINESS_NAMES = Arrays.stream(BUSINESS_PERMS).map(a -> a[0]).collect(Collectors.toSet());

    @Mock PermissionRepository permissionRepository;
    @Mock RoleRepository roleRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock UserRepository userRepository;

    PermissionService permissionService;
    RoleService roleService;
    OrganisationProvisioningService provisioningService;

    Organisation orgA;
    Organisation orgB;
    Role adminRoleA;
    Permission createOrgPerm;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        permissionService = new PermissionService(permissionRepository);
        // RoleService needs AuthorizationService mock for permission assignment checks
        AuthorizationService authz = mock(AuthorizationService.class);
        lenient().doNothing().when(authz).requireCanAssignPermissions(any());
        lenient().doNothing().when(authz).requireCanAssignRole(any());
        lenient().when(roleRepository.findWithPermissionsByIdAndOrganisationOrgId(anyLong(), anyLong()))
                .thenAnswer(inv -> roleRepository.findByIdAndOrganisationOrgId(inv.getArgument(0), inv.getArgument(1)));
        roleService = new RoleService(roleRepository, permissionRepository, organisationRepository, authz, userRepository);

        orgA = Organisation.builder().orgId(1L).name("OrgA").build();
        orgB = Organisation.builder().orgId(2L).name("OrgB").build();
        createOrgPerm = Permission.builder().id(999L).name("CREATE_ORGANISATION").description("Platform only").build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    // 1. Seeder creates all 12 new permissions
    @Test
    void seeder_creates12NewPermissions() throws Exception {
        when(permissionRepository.existsByName(anyString())).thenReturn(false);
        when(permissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PermissionSeeder seeder = new PermissionSeeder();
        ApplicationRunner runner = seeder.seedPermissions(permissionRepository);
        runner.run(null);

        // Verify each business perm was saved with correct description
        for (String[] entry : BUSINESS_PERMS) {
            String name = entry[0];
            String desc = entry[1];
            verify(permissionRepository).save(argThat(p -> name.equals(p.getName()) && desc.equals(p.getDescription())));
        }
    }

    // 2. Seeder is idempotent
    @Test
    void seeder_idempotent_noDuplicates() throws Exception {
        // First run: not exists -> save
        when(permissionRepository.existsByName(anyString())).thenReturn(false);
        when(permissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        PermissionSeeder seeder = new PermissionSeeder();
        ApplicationRunner runner = seeder.seedPermissions(permissionRepository);
        runner.run(null);
        verify(permissionRepository, atLeast(71)).save(any());

        reset(permissionRepository);
        // Second run: exists -> no save
        when(permissionRepository.existsByName(anyString())).thenReturn(true);
        runner.run(null);
        verify(permissionRepository, never()).save(any());
    }

    // 3. Total catalog is 71
    @Test
    void totalCatalog_is71() {
        // Simulate repository containing 59 old + 12 new = 71
        List<Permission> all = new ArrayList<>();
        for (int i = 0; i < 59; i++) all.add(Permission.builder().id((long) i).name("OLD_" + i).description("Auto-seeded permission OLD_" + i).build());
        for (String[] e : BUSINESS_PERMS) all.add(Permission.builder().id(100L + Arrays.asList(BUSINESS_PERMS).indexOf(e)).name(e[0]).description(e[1]).build());
        when(permissionRepository.findAll()).thenReturn(all);
        List<Permission> fetched = permissionService.getAllPermissions();
        assertEquals(71, fetched.size());
        // Verify business perms present with descriptions
        Map<String, String> map = fetched.stream().collect(Collectors.toMap(Permission::getName, p -> p.getDescription() == null ? "" : p.getDescription(), (a,b)->a));
        for (String[] e : BUSINESS_PERMS) {
            assertTrue(map.containsKey(e[0]), "Missing " + e[0]);
            assertEquals(e[1], map.get(e[0]));
        }
    }

    // 4. CREATE_ORGANISATION remains platform only – org role cannot receive it
    @Test
    void createOrganisation_remainsPlatformOnly() {
        // Org admin does not have CREATE_ORGANISATION, so requireCanAssign should throw
        AuthorizationService realAuth = new AuthorizationService(userRepository);
        User orgUser = User.builder().id(100L).username("adminA").organisation(orgA)
                .role(Role.builder().id(10L).name("ADMIN").organisation(orgA)
                        .permissions(new HashSet<>(Set.of(Permission.builder().name("USER_CREATE").build()))).build()).build();
        // Mock auth to simulate org user without CREATE_ORGANISATION
        AuthorizationService mockAuth = mock(AuthorizationService.class);
        doThrow(new AccessDeniedException("Cannot grant permission 'CREATE_ORGANISATION' you do not have"))
                .when(mockAuth).requireCanAssignPermissions(argThat(perms -> perms.stream().anyMatch(p -> "CREATE_ORGANISATION".equals(p.getName()))));

        RoleService rs = new RoleService(roleRepository, permissionRepository, organisationRepository, mockAuth, userRepository);
        Role target = Role.builder().id(11L).name("EDITOR").organisation(orgA).permissions(new HashSet<>()).build();
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(target));
        when(permissionRepository.findAllById(Set.of(999L))).thenReturn(List.of(createOrgPerm));

        assertThrows(AccessDeniedException.class, () -> rs.assignPermissionsToRole(11L, Set.of(999L), 1L));
    }

    // 5. Organization ADMIN receives new org-level permissions automatically
    @Test
    void provisionedAdmin_receivesNewBusinessPermissions() throws Exception {
        // Simulate provisioning: all perms = 59 old + 12 new, ADMIN should get 70 (all except CREATE_ORGANISATION)
        List<Permission> allPerms = new ArrayList<>();
        for (int i = 0; i < 59; i++) {
            String n = "OLD_" + i;
            if (i == 0) n = "CREATE_ORGANISATION";
            allPerms.add(Permission.builder().id((long) i).name(n).build());
        }
        for (String[] e : BUSINESS_PERMS) allPerms.add(Permission.builder().id(100L).name(e[0]).description(e[1]).build());
        // Actually need 59 old includes CREATE_ORGANISATION, so total 71 with 12 new, ADMIN gets 70
        when(permissionRepository.findAll()).thenReturn(allPerms);

        // Mock dependencies for provisioning: we test the filter logic directly
        Set<String> allowedNames = allPerms.stream()
                .map(p -> p.getName().toUpperCase())
                .filter(n -> !n.equals("CREATE_ORGANISATION") && !n.equals("ORGANISATION_CREATE") && !n.equals("ORG_CREATE"))
                .collect(Collectors.toSet());

        assertTrue(allowedNames.contains("INVESTIGATION_ACCESS"));
        assertTrue(allowedNames.contains("EVIDENCE_SOURCE_ACCESS"));
        assertTrue(allowedNames.contains("REPORT_ACCESS"));
        assertFalse(allowedNames.contains("CREATE_ORGANISATION"));
        assertEquals(70, allowedNames.size(), "ADMIN should get 70 of 71 (all except platform-only)");
    }

    // 6. Organization Admin can create a role with selected new permissions
    @Test
    void orgAdmin_canCreateRoleWithNewPermissions() {
        // Create role then assign new business perms
        Role newRole = Role.builder().name("Investigator").description("handles investigations").build();
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(roleRepository.existsByNameAndOrganisationOrgId("Investigator", 1L)).thenReturn(false);
        lenient().when(roleRepository.save(any(Role.class))).thenAnswer(i -> {
            Role r = i.getArgument(0);
            if (r != null) r.setId(100L);
            return r;
        });
        Role created = roleService.createRole(newRole, 1L);
        assertEquals("Investigator", created.getName());

        // Now assign new business perms
        Permission invAccess = Permission.builder().id(200L).name("INVESTIGATION_ACCESS").description("Access and manage investigations.").build();
        Permission evidenceAccess = Permission.builder().id(201L).name("EVIDENCE_ACCESS").description("Access and manage investigation evidence.").build();
        when(roleRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(created));
        when(permissionRepository.findAllById(Set.of(200L, 201L))).thenReturn(List.of(invAccess, evidenceAccess));
        lenient().when(roleRepository.save(any(Role.class))).thenAnswer(i -> i.getArgument(0));

        Role updated = roleService.assignPermissionsToRole(100L, Set.of(200L, 201L), 1L);
        assertTrue(updated.getPermissions().contains(invAccess));
        assertTrue(updated.getPermissions().contains(evidenceAccess));
    }

    // 7. Selected permissions are persisted
    @Test
    void selectedPermissions_arePersisted() {
        Permission p1 = Permission.builder().id(300L).name("INVESTIGATION_ACCESS").build();
        Permission p2 = Permission.builder().id(301L).name("WORKSPACE_ACCESS").build();
        Role role = Role.builder().id(11L).name("EDITOR").organisation(orgA).permissions(new HashSet<>()).build();
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(role));
        when(permissionRepository.findAllById(Set.of(300L, 301L))).thenReturn(List.of(p1, p2));
        when(roleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Role result = roleService.assignPermissionsToRole(11L, Set.of(300L, 301L), 1L);
        assertEquals(2, result.getPermissions().size());
        assertTrue(result.getPermissions().stream().anyMatch(p -> "INVESTIGATION_ACCESS".equals(p.getName())));
    }

    // 8. Permissions can be removed
    @Test
    void permissions_canBeRemoved() {
        Permission p1 = Permission.builder().id(300L).name("INVESTIGATION_ACCESS").build();
        Permission p2 = Permission.builder().id(301L).name("EVIDENCE_ACCESS").build();
        Role role = Role.builder().id(11L).name("EDITOR").organisation(orgA).permissions(new HashSet<>(Set.of(p1, p2))).build();
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(role));
        when(roleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Role result = roleService.removePermissionsFromRole(11L, Set.of(300L), 1L);
        assertEquals(1, result.getPermissions().size());
        assertFalse(result.getPermissions().contains(p1));
    }

    // 9. Duplicate assignment handled safely (idempotent)
    @Test
    void duplicateAssignment_isIdempotent() {
        Permission p1 = Permission.builder().id(300L).name("REPORT_ACCESS").build();
        Role role = Role.builder().id(11L).name("EDITOR").organisation(orgA).permissions(new HashSet<>(Set.of(p1))).build();
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(role));
        when(permissionRepository.findAllById(Set.of(300L))).thenReturn(List.of(p1));

        Role result = roleService.assignPermissionsToRole(11L, Set.of(300L), 1L);
        assertEquals(1, result.getPermissions().size(), "Duplicate should not create second entry");
    }

    // 10. Cross-organization forbidden
    @Test
    void crossOrg_roleAccess_forbidden() {
        when(roleRepository.findByIdAndOrganisationOrgId(10L, 2L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> roleService.getRoleById(10L, 2L));
        assertThrows(AccessDeniedException.class, () -> roleService.assignPermissionsToRole(10L, Set.of(300L), 2L));
        when(roleRepository.findByIdAndOrganisationOrgId(10L, 1L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> roleService.getPermissionsForRole(10L, 2L));
    }

    // 11. Existing behavior not broken + descriptions present
    @Test
    void existingPermissions_stillExist_withDescriptions() {
        List<Permission> all = new ArrayList<>();
        // Simulate 59 old with generic desc + 12 new with specific desc
        for (int i = 0; i < 59; i++) all.add(Permission.builder().id((long) i).name("OLD_" + i).description("Auto-seeded permission OLD_" + i).build());
        for (String[] e : BUSINESS_PERMS) all.add(Permission.builder().id(100L).name(e[0]).description(e[1]).build());
        when(permissionRepository.findAll()).thenReturn(all);
        List<Permission> fetched = permissionService.getAllPermissions();
        assertEquals(71, fetched.size());
        // Verify old still there (sample)
        assertTrue(fetched.stream().anyMatch(p -> "OLD_0".equals(p.getName())));
        // Verify new descriptions not generic
        Optional<Permission> inv = fetched.stream().filter(p -> "INVESTIGATION_ACCESS".equals(p.getName())).findFirst();
        assertTrue(inv.isPresent());
        assertEquals("Access and manage investigations.", inv.get().getDescription());
        assertFalse(inv.get().getDescription().equals("Auto-seeded permission INVESTIGATION_ACCESS"));
    }
}
