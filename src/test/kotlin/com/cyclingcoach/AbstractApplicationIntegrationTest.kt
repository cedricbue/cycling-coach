package com.cyclingcoach

import com.cyclingcoach.activity.ActivityRepository
import com.cyclingcoach.sync.GarminTokenStore
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * Base class for all integration tests.
 *
 * [wireMock] is a JVM-level singleton — it starts once on a fixed dynamic port and is never
 * stopped between test classes. This keeps the port stable so Spring's cached application
 * context can reuse the same Garmin base-URL properties across all test classes.
 *
 * WireMockExtension was intentionally avoided: its JUnit lifecycle stops and restarts the
 * server between test classes, which changes the port and breaks the cached context.
 */
@SpringBootTest
@ActiveProfiles("test")
abstract class AbstractApplicationIntegrationTest {
    @Autowired lateinit var dsl: DSLContext

    @Autowired lateinit var activityRepository: ActivityRepository

    @Autowired lateinit var garminTokenStore: GarminTokenStore

    /** Reset all stubs and re-register the base auth stubs before every test method. */
    @BeforeEach
    fun resetBaseStubs() {
        wireMock.resetAll()
        stubGarminAuthFlow()
    }

    companion object {
        /**
         * WireMock singleton: starts once on JVM initialisation, never restarted.
         * All test classes share the same port, so Spring's context cache is not invalidated.
         */
        val wireMock: WireMockServer =
            WireMockServer(wireMockConfig().dynamicPort()).also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun garminProperties(registry: DynamicPropertyRegistry) {
            registry.add("sync.garmin.email") { "test@example.com" }
            registry.add("sync.garmin.password") { "test-password" }
            registry.add("garmin.connect.sso-base-url") { "http://localhost:${wireMock.port()}" }
            registry.add("garmin.connect.di-auth-base-url") { "http://localhost:${wireMock.port()}" }
            registry.add("garmin.connect.api-base-url") { "http://localhost:${wireMock.port()}" }
            // Stubs must exist before ApplicationReadyEvent fires GarminSyncJob.authenticateOnStartup
            stubGarminAuthFlow()
        }

        /**
         * Registers WireMock stubs for the 2-step Garmin DI token auth flow:
         * 1. POST /mobile/api/login → serviceTicketId
         * 2. POST /di-oauth2-service/oauth/token → DI Bearer token
         */
        fun stubGarminAuthFlow() {
            wireMock.stubFor(
                post(urlPathMatching("/mobile/api/login"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """{"responseStatus":{"type":"SUCCESSFUL"},"serviceTicketId":"ST-TEST-TICKET"}""",
                            ),
                    ),
            )
            wireMock.stubFor(
                post(urlPathMatching("/di-oauth2-service/oauth/token"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """{"access_token":"test-access-token","refresh_token":"test-refresh-token","expires_in":3600,"refresh_token_expires_in":7776000}""",
                            ),
                    ),
            )
        }
    }
}
