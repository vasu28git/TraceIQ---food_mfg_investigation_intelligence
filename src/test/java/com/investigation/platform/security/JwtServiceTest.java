package com.investigation.platform.security;

import com.investigation.platform.user.entity.User;
import com.investigation.platform.user.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", 3600000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpirationMs", 86400000L);
    }

    @Test
    void testTokenGenerationAndValidation() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .userId(userId)
                .orgId(orgId)
                .email("investigator@platform.com")
                .passwordHash("hashedPass")
                .status(UserStatus.ACTIVE)
                .build();

        SecurityUser securityUser = new SecurityUser(user);
        String token = jwtService.generateToken(securityUser);

        assertNotNull(token);
        assertTrue(jwtService.isTokenValid(token));
        assertEquals("investigator@platform.com", jwtService.extractUsername(token));
        assertEquals(orgId, jwtService.extractOrgId(token));
        assertEquals(userId, jwtService.extractUserId(token));
    }
}
