package com.taceiq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taceiq.dto.UserResponse;
import com.taceiq.entity.Organisation;
import com.taceiq.entity.Role;
import com.taceiq.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UserResponseSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    {
        mapper.findAndRegisterModules();
    }

    private User buildUser(Long id, String username, Organisation org, Role role) {
        return User.builder()
                .id(id)
                .username(username)
                .password("$2a$10$hashedpasswordshouldnotbeexposed")
                .status("ACTIVE")
                .mustChangePassword(false)
                .organisation(org)
                .role(role)
                .build();
    }

    @Test
    void fromEntity_mapsOrganisationAndRoleAsDto() {
        Organisation org = Organisation.builder().orgId(42L).name("TestOrg").build();
        Role role = Role.builder().id(7L).name("EDITOR").description("edit role").organisation(org).build();
        User user = buildUser(100L, "alice", org, role);

        UserResponse resp = UserResponse.fromEntity(user);

        assertNotNull(resp);
        assertEquals(100L, resp.getId());
        assertEquals("alice", resp.getUsername());
        assertEquals("ACTIVE", resp.getStatus());
        assertNotNull(resp.getOrganisation());
        assertEquals(42L, resp.getOrganisation().getOrgId());
        assertEquals("TestOrg", resp.getOrganisation().getName());
        assertNotNull(resp.getRole());
        assertEquals(7L, resp.getRole().getId());
        assertEquals("EDITOR", resp.getRole().getName());
        assertEquals("edit role", resp.getRole().getDescription());
    }

    @Test
    void fromEntity_nullOrganisationAndRole_handled() {
        User user = buildUser(101L, "bob", null, null);
        UserResponse resp = UserResponse.fromEntity(user);
        assertNotNull(resp);
        assertNull(resp.getOrganisation());
        assertNull(resp.getRole());
    }

    @Test
    void fromEntity_nullInput_returnsNull() {
        assertNull(UserResponse.fromEntity(null));
    }

    @Test
    void json_doesNotExposePasswordOrSecrets() throws Exception {
        Organisation org = Organisation.builder().orgId(1L).name("OrgA").build();
        Role role = Role.builder().id(10L).name("ADMIN").description("admin").organisation(org).build();
        User user = buildUser(1L, "alice", org, role);

        UserResponse resp = UserResponse.fromEntity(user);
        String json = mapper.writeValueAsString(resp);

        assertFalse(json.contains("hashedpasswordshouldnotbeexposed"), "password hash must not be exposed");
        assertFalse(json.toLowerCase().contains("\"password\""), "password field must not be exposed");
        assertFalse(json.contains("ByteBuddyInterceptor"), "should not expose ByteBuddyInterceptor");
        assertFalse(json.contains("hibernateLazyInitializer"), "should not expose hibernateLazyInitializer");
        assertFalse(json.contains("handler"), "should not expose handler");
        assertFalse(json.contains("secret"), "should not expose secret");
    }

    @Test
    void json_serializesSuccessfullyWithoutByteBuddyInterceptor() throws Exception {
        // Simulate Hibernate proxy scenario: entity with lazy associations that would previously be serialized as proxy
        Organisation org = Organisation.builder().orgId(99L).name("ProxyOrg").build();
        Role role = Role.builder().id(55L).name("VIEWER").description("viewer").organisation(org).build();
        // User is built with org/role that in real JPA would be proxies; fromEntity must copy to plain DTO, not retain proxy
        User user = buildUser(200L, "proxyUser", org, role);

        UserResponse resp = UserResponse.fromEntity(user);
        // Must be plain DTO, not entity type
        assertNotNull(resp.getOrganisation());
        assertEquals(org.getOrgId(), resp.getOrganisation().getOrgId());
        // Ensure type is DTO, not entity
        assertEquals(UserResponse.OrganisationSummary.class, resp.getOrganisation().getClass());
        assertEquals(UserResponse.RoleSummary.class, resp.getRole().getClass());

        String json = mapper.writeValueAsString(resp);
        assertTrue(json.contains("\"orgId\":99"));
        assertTrue(json.contains("\"name\":\"ProxyOrg\""));
        assertTrue(json.contains("\"id\":55"));
        // No proxy fields
        assertFalse(json.contains("ByteBuddy"));
    }

    @Test
    void json_containsExpectedSafeFields() throws Exception {
        Organisation org = Organisation.builder().orgId(5L).name("hutsan").build();
        Role role = Role.builder().id(4L).name("ADMIN").description("Initial ADMIN role").organisation(org).build();
        User user = User.builder().id(10L).username("smokeorg_admin").status("ACTIVE").mustChangePassword(true).organisation(org).role(role).build();

        UserResponse resp = UserResponse.fromEntity(user);
        String json = mapper.writeValueAsString(resp);

        assertTrue(json.contains("\"username\":\"smokeorg_admin\""));
        assertTrue(json.contains("\"status\":\"ACTIVE\""));
        assertTrue(json.contains("\"mustChangePassword\":true"));
        assertTrue(json.contains("\"orgId\":5"));
        assertTrue(json.contains("\"name\":\"hutsan\""));
        assertTrue(json.contains("\"id\":4"));
        assertTrue(json.contains("\"name\":\"ADMIN\""));
        // No password, no organisation users/roles/collections leaked
        assertFalse(json.contains("\"users\""));
        assertFalse(json.contains("\"roles\""));
        assertFalse(json.contains("\"permissions\"")); // role permissions not in UserResponse DTO by design (safe)
    }

    @Test
    void listSerialization_doesNotContainProxyFields() throws Exception {
        Organisation org = Organisation.builder().orgId(6L).name("Org6").build();
        Role role = Role.builder().id(1L).name("ADMIN").organisation(org).build();
        User u1 = buildUser(1L, "alice", org, role);
        User u2 = buildUser(2L, "bob", org, role);

        UserResponse r1 = UserResponse.fromEntity(u1);
        UserResponse r2 = UserResponse.fromEntity(u2);
        String json = mapper.writeValueAsString(java.util.List.of(r1, r2));

        assertTrue(json.contains("alice"));
        assertTrue(json.contains("bob"));
        assertFalse(json.contains("ByteBuddyInterceptor"));
        assertFalse(json.contains("hibernateLazyInitializer"));
        assertFalse(json.contains("\"password\""));
    }
}
