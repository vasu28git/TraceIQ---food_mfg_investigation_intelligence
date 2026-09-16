package com.taceiq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taceiq.dto.UserCreateRequest;
import com.taceiq.dto.UserResponse;
import com.taceiq.dto.UserUpdateRequest;
import com.taceiq.entity.Organisation;
import com.taceiq.entity.Permission;
import com.taceiq.entity.Role;
import com.taceiq.entity.User;
import com.taceiq.repository.OrganisationRepository;
import com.taceiq.repository.RoleRepository;
import com.taceiq.repository.UserRepository;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.UserService;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class UserManagementTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock AuthorizationService authorizationService;

    UserService userService;
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    Organisation orgA;
    Organisation orgB;
    Role adminRoleA;
    Role viewerRoleA;
    Role adminRoleB;
    Permission userCreatePerm;
    Permission userReadPerm;
    Permission userUpdatePerm;
    Permission userDeletePerm;
    User adminUser;
    User viewerUser;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        userService = new UserService(userRepository, organisationRepository, roleRepository, encoder, authorizationService);

        orgA = Organisation.builder().orgId(1L).name("OrgA").build();
        orgB = Organisation.builder().orgId(2L).name("OrgB").build();

        userCreatePerm = Permission.builder().id(1L).name("USER_CREATE").build();
        userReadPerm = Permission.builder().id(2L).name("USER_READ").build();
        userUpdatePerm = Permission.builder().id(3L).name("USER_UPDATE").build();
        userDeletePerm = Permission.builder().id(4L).name("USER_DELETE").build();

        adminRoleA = Role.builder().id(10L).name("ADMIN").organisation(orgA)
                .permissions(new HashSet<>(Set.of(userCreatePerm, userReadPerm, userUpdatePerm, userDeletePerm))).build();
        viewerRoleA = Role.builder().id(11L).name("VIEWER").organisation(orgA)
                .permissions(new HashSet<>(Set.of(userReadPerm))).build();
        adminRoleB = Role.builder().id(20L).name("ADMIN").organisation(orgB)
                .permissions(new HashSet<>(Set.of(userCreatePerm, userReadPerm))).build();

        adminUser = User.builder().id(100L).username("adminA").password(encoder.encode("pass")).organisation(orgA).role(adminRoleA).status("ACTIVE").build();
        viewerUser = User.builder().id(101L).username("viewerA").password(encoder.encode("pass")).organisation(orgA).role(viewerRoleA).status("ACTIVE").build();

        // default: allow assign for admin perms and mock current user for self-checks
        doNothing().when(authorizationService).requireCanAssignRole(any());
        doNothing().when(authorizationService).requireCanAssignPermissions(any());
        lenient().when(authorizationService.getCurrentUser()).thenReturn(adminUser);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    void setAuth(String username, String... authorities) {
        List<SimpleGrantedAuthority> granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(username, null, granted);
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(username.equals("adminA") ? adminUser : viewerUser));
    }

    // --- Create ---
    @Test
    void createUser_successfully() {
        UserCreateRequest req = UserCreateRequest.builder().username("newUser").password("Password123!").roleId(11L).status("ACTIVE").build();
        when(userRepository.existsByUsername("newUser")).thenReturn(false);
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(viewerRoleA));
        when(userRepository.save(any())).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(200L);
            return u;
        });

        User created = userService.createUserFromRequest(req, 1L);
        assertEquals("newUser", created.getUsername());
        assertEquals(1L, created.getOrganisation().getOrgId());
        assertTrue(created.getMustChangePassword());
        assertEquals("ACTIVE", created.getStatus());
        assertTrue(encoder.matches("Password123!", created.getPassword()));
        verify(authorizationService).requireCanAssignRole(viewerRoleA);
    }

    @Test
    void createUser_duplicateUsername_409() {
        UserCreateRequest req = UserCreateRequest.builder().username("adminA").password("Password123!").roleId(11L).build();
        when(userRepository.existsByUsername("adminA")).thenReturn(true);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> userService.createUserFromRequest(req, 1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void createUser_forcesAuthenticatedOrganization() {
        // Attempt to create with fake org should still be forced to 1L
        User user = User.builder().username("forcedOrg").password("Password123!").role(viewerRoleA).organisation(orgB).build();
        when(userRepository.existsByUsername("forcedOrg")).thenReturn(false);
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(viewerRoleA));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        User created = userService.createUser(user, 1L);
        assertEquals(1L, created.getOrganisation().getOrgId());
        assertNotEquals(2L, created.getOrganisation().getOrgId());
    }

    @Test
    void createUser_crossOrgRole_rejected_403() {
        UserCreateRequest req = UserCreateRequest.builder().username("crossRole").password("Password123!").roleId(20L).build();
        when(userRepository.existsByUsername("crossRole")).thenReturn(false);
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(roleRepository.findByIdAndOrganisationOrgId(20L, 1L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> userService.createUserFromRequest(req, 1L));
    }

    @Test
    void createUser_unauthorizedRoleEscalation_403() {
        // viewer (only USER_READ) tries to assign admin role (which has USER_CREATE etc.)
        doThrow(new AccessDeniedException("Cannot assign role with permission 'USER_CREATE' you do not have"))
                .when(authorizationService).requireCanAssignRole(adminRoleA);
        UserCreateRequest req = UserCreateRequest.builder().username("escalate").password("Password123!").roleId(10L).build();
        when(userRepository.existsByUsername("escalate")).thenReturn(false);
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(roleRepository.findByIdAndOrganisationOrgId(10L, 1L)).thenReturn(Optional.of(adminRoleA));
        assertThrows(AccessDeniedException.class, () -> userService.createUserFromRequest(req, 1L));
    }

    @Test
    void createUser_passwordIsEncoded() {
        UserCreateRequest req = UserCreateRequest.builder().username("encTest").password("MySecret123").roleId(11L).build();
        when(userRepository.existsByUsername("encTest")).thenReturn(false);
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(viewerRoleA));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        User created = userService.createUserFromRequest(req, 1L);
        assertNotEquals("MySecret123", created.getPassword());
        assertTrue(created.getPassword().startsWith("$2a$"));
        assertTrue(encoder.matches("MySecret123", created.getPassword()));
    }

    // --- List ---
    @Test
    void listUsers_onlyCurrentOrg() {
        when(userRepository.findByOrganisationOrgId(1L)).thenReturn(List.of(adminUser, viewerUser));
        List<User> list = userService.listUsers(1L);
        assertEquals(2, list.size());
        for (User u : list) assertEquals(1L, u.getOrganisation().getOrgId());
        // cross-org via getUsersByOrg should 403
        assertThrows(AccessDeniedException.class, () -> userService.getUsersByOrg(2L, 1L));
    }

    // --- Get ---
    @Test
    void getUser_crossOrg_rejected_403() {
        when(userRepository.findByIdAndOrganisationOrgId(200L, 1L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> userService.getUserById(200L, 1L));
        when(userRepository.findByUsernameAndOrganisationOrgId("bob", 1L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> userService.getUserByUsername("bob", 1L));
    }

    @Test
    void getUser_success() {
        when(userRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(adminUser));
        User u = userService.getUserById(100L, 1L);
        assertEquals("adminA", u.getUsername());
    }

    // --- Update ---
    @Test
    void updateUser_successfully() {
        when(userRepository.findByIdAndOrganisationOrgId(101L, 1L)).thenReturn(Optional.of(viewerUser));
        when(userRepository.existsByUsername("newName")).thenReturn(false);
        when(userRepository.findByUsernameAndOrganisationOrgId("newName", 1L)).thenReturn(Optional.empty());
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(viewerRoleA));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserUpdateRequest req = UserUpdateRequest.builder().username("newName").status("INACTIVE").roleId(11L).build();
        // need to handle last admin checks: viewer is not admin, so no count needed
        when(userRepository.findByOrganisationOrgId(1L)).thenReturn(List.of(adminUser, viewerUser));

        User updated = userService.updateUser(101L, 1L, req);
        assertEquals("newName", updated.getUsername());
        assertEquals("INACTIVE", updated.getStatus());
    }

    @Test
    void updateUser_cannotChangeOrganization() {
        when(userRepository.findByIdAndOrganisationOrgId(101L, 1L)).thenReturn(Optional.of(viewerUser));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        // Even if request tries to imply org change via role, role is validated within org, so org remains 1L
        UserUpdateRequest req = UserUpdateRequest.builder().username("viewerA").roleId(11L).build();
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(viewerRoleA));
        User updated = userService.updateUser(101L, 1L, req);
        assertEquals(1L, updated.getOrganisation().getOrgId());
        // Try to smuggle orgB role - should 403
        when(roleRepository.findByIdAndOrganisationOrgId(20L, 1L)).thenReturn(Optional.empty());
        UserUpdateRequest badReq = UserUpdateRequest.builder().roleId(20L).build();
        assertThrows(AccessDeniedException.class, () -> userService.updateUser(101L, 1L, badReq));
    }

    @Test
    void updateUser_crossOrgRole_rejected() {
        when(userRepository.findByIdAndOrganisationOrgId(101L, 1L)).thenReturn(Optional.of(viewerUser));
        when(roleRepository.findByIdAndOrganisationOrgId(20L, 1L)).thenReturn(Optional.empty());
        UserUpdateRequest req = UserUpdateRequest.builder().roleId(20L).build();
        assertThrows(AccessDeniedException.class, () -> userService.updateUser(101L, 1L, req));
    }

    @Test
    void updateUser_unauthorizedEscalation_403() {
        when(userRepository.findByIdAndOrganisationOrgId(101L, 1L)).thenReturn(Optional.of(viewerUser));
        when(roleRepository.findByIdAndOrganisationOrgId(10L, 1L)).thenReturn(Optional.of(adminRoleA));
        doThrow(new AccessDeniedException("Cannot assign role with permission 'USER_CREATE' you do not have"))
                .when(authorizationService).requireCanAssignRole(adminRoleA);
        UserUpdateRequest req = UserUpdateRequest.builder().roleId(10L).build();
        assertThrows(AccessDeniedException.class, () -> userService.updateUser(101L, 1L, req));
    }

    @Test
    void updateUser_unauthorizedPermission_403_viaAuthz() {
        // Simulate controller check before service
        AuthorizationService realAuth = new AuthorizationService(userRepository);
        setAuth("viewerA", "USER_READ"); // only read, not update
        when(userRepository.findByUsername("viewerA")).thenReturn(Optional.of(viewerUser));
        assertThrows(AccessDeniedException.class, () -> realAuth.requireUserUpdate());
    }

    // --- Status ---
    @Test
    void deactivate_activate() {
        when(userRepository.findByIdAndOrganisationOrgId(101L, 1L)).thenReturn(Optional.of(viewerUser));
        when(userRepository.findByOrganisationOrgId(1L)).thenReturn(List.of(adminUser, viewerUser)); // admin still active, so deactivation allowed for viewer
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        User deactivated = userService.updateUserStatus(101L, 1L, "INACTIVE");
        assertEquals("INACTIVE", deactivated.getStatus());
        // reactivate
        deactivated.setStatus("INACTIVE");
        when(userRepository.findByIdAndOrganisationOrgId(101L, 1L)).thenReturn(Optional.of(deactivated));
        User reactivated = userService.updateUserStatus(101L, 1L, "ACTIVE");
        assertEquals("ACTIVE", reactivated.getStatus());
    }

    @Test
    void deactivate_lastAdmin_400() {
        // adminUser is only active admin
        User onlyAdmin = User.builder().id(100L).username("adminA").organisation(orgA).role(adminRoleA).status("ACTIVE").build();
        when(userRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(onlyAdmin));
        when(userRepository.findByOrganisationOrgId(1L)).thenReturn(List.of(onlyAdmin));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> userService.updateUserStatus(100L, 1L, "INACTIVE"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    // --- Delete ---
    @Test
    void deleteUser_success() {
        when(userRepository.findByIdAndOrganisationOrgId(101L, 1L)).thenReturn(Optional.of(viewerUser));
        when(userRepository.findByOrganisationOrgId(1L)).thenReturn(List.of(adminUser, viewerUser));
        // need current user for self-delete check
        AuthorizationService realAuth = new AuthorizationService(userRepository);
        setAuth("adminA", "USER_DELETE");
        when(userRepository.findByUsername("adminA")).thenReturn(Optional.of(adminUser));
        // Use real auth for delete's self check - need to inject real auth into service for this test
        // Instead mock auth to allow
        when(authorizationService.getCurrentUser()).thenReturn(adminUser);
        userService.deleteUser(101L, 1L);
        verify(userRepository).delete(viewerUser);
    }

    @Test
    void deleteUser_crossOrg_403() {
        when(userRepository.findByIdAndOrganisationOrgId(200L, 1L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> userService.deleteUser(200L, 1L));
    }

    @Test
    void deleteUser_self_400() {
        when(userRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(adminUser));
        when(authorizationService.getCurrentUser()).thenReturn(adminUser);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> userService.deleteUser(100L, 1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void deleteUser_lastAdmin_400() {
        User onlyAdmin = User.builder().id(100L).username("adminA").organisation(orgA).role(adminRoleA).status("ACTIVE").build();
        when(userRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(onlyAdmin));
        when(authorizationService.getCurrentUser()).thenReturn(User.builder().id(999L).username("other").organisation(orgA).role(adminRoleA).build());
        when(userRepository.findByOrganisationOrgId(1L)).thenReturn(List.of(onlyAdmin));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> userService.deleteUser(100L, 1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    // --- Password & mustChangePassword ---
    @Test
    void passwordChange_works() {
        // Use real AuthService logic via UserService? Actually password change via AuthService, but we test UserService password encoding + mustChangePassword
        // Create user should have mustChangePassword true
        UserCreateRequest req = UserCreateRequest.builder().username("pwdUser").password("Secret123!").roleId(11L).build();
        when(userRepository.existsByUsername("pwdUser")).thenReturn(false);
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(viewerRoleA));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        User created = userService.createUserFromRequest(req, 1L);
        assertTrue(created.getMustChangePassword());
        assertTrue(encoder.matches("Secret123!", created.getPassword()));
    }

    @Test
    void passwordHash_neverReturned() throws Exception {
        User user = User.builder().id(100L).username("adminA").password("$2a$10$hashed").organisation(orgA).role(adminRoleA).status("ACTIVE").mustChangePassword(false).build();
        UserResponse resp = UserResponse.fromEntity(user);
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        String json = mapper.writeValueAsString(resp);
        assertFalse(json.contains("hashed"), "UserResponse should not contain password hash");
        assertFalse(json.contains("\"password\""), "UserResponse should not contain password field");
        // Also test entity serialization directly (should hide password due to WRITE_ONLY)
        String entityJson = mapper.writeValueAsString(user);
        assertFalse(entityJson.contains("hashed"), "Entity JSON should not contain password hash");
        assertFalse(entityJson.contains("\"password\""), "Entity JSON should not contain password field");
        // mustChangePassword field is expected and should be present, but not password field
        assertTrue(json.contains("mustChangePassword"));
    }

    @Test
    void mustChangePassword_preservedOnUpdate() {
        viewerUser.setMustChangePassword(true);
        when(userRepository.findByIdAndOrganisationOrgId(101L, 1L)).thenReturn(Optional.of(viewerUser));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        UserUpdateRequest req = UserUpdateRequest.builder().status("ACTIVE").roleId(11L).build();
        when(roleRepository.findByIdAndOrganisationOrgId(11L, 1L)).thenReturn(Optional.of(viewerRoleA));
        when(userRepository.findByOrganisationOrgId(1L)).thenReturn(List.of(adminUser, viewerUser));
        User updated = userService.updateUser(101L, 1L, req);
        assertTrue(updated.getMustChangePassword()); // should remain true until changed via AuthService
    }
}
