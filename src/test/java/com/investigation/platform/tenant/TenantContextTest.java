package com.investigation.platform.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {

    @BeforeEach
    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void testSetAndGetTenantId() {
        UUID testOrgId = UUID.randomUUID();
        TenantContext.setTenantId(testOrgId);

        assertEquals(testOrgId, TenantContext.getTenantId());
        assertEquals(testOrgId, TenantContext.getRequiredTenantId());
    }

    @Test
    void testClearTenantId() {
        UUID testOrgId = UUID.randomUUID();
        TenantContext.setTenantId(testOrgId);
        TenantContext.clear();

        assertNull(TenantContext.getTenantId());
        assertThrows(IllegalStateException.class, TenantContext::getRequiredTenantId);
    }
}
