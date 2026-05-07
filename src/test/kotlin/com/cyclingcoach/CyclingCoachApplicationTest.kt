package com.cyclingcoach

import org.junit.jupiter.api.Test

class CyclingCoachApplicationTest : AbstractApplicationIntegrationTest() {
    @Test
    fun contextLoads() {
        // Spring context started, Flyway ran V1 migration, jOOQ is wired,
        // and GarminSyncJob authenticated via the WireMock SSO stubs.
    }
}
