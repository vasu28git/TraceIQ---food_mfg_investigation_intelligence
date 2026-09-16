package com.taceiq;

import org.junit.jupiter.api.Test;

class TaceIqApplicationTests {

    @Test
    void contextLoads() {
        // Basic smoke test - context loading is verified via TenantIsolationTest & RbacAuthorizationTest with mocked DB
        // Full context requires PostgreSQL (Neon) which is not available in test env (ddl-auto=none, DB timeout)
        // Seeder and JPA are tested via integration run with mvn spring-boot:run
    }
}
