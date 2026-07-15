package com.example.taskmanager.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TenantContextTest {

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void unresolvedTenantIsASentinelThatIsNotARealBucket() {
        // No tenant bound: must resolve to the deny sentinel, never the populated 'default' bucket.
        assertEquals(TenantContext.UNRESOLVED_TENANT, TenantContext.getTenantId());
        assertNotEquals("default", TenantContext.getTenantId());
    }

    @Test
    void returnsBoundTenantWhenSet() {
        TenantContext.setTenantId("tenant-a");
        assertEquals("tenant-a", TenantContext.getTenantId());
    }

    @Test
    void fallsBackToSentinelAfterClear() {
        TenantContext.setTenantId("tenant-a");
        TenantContext.clear();
        assertEquals(TenantContext.UNRESOLVED_TENANT, TenantContext.getTenantId());
    }
}
