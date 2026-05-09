package com.cyclingcoach.garmin.connect

import com.cyclingcoach.garmin.connect.client.GarminConnect
import com.cyclingcoach.garmin.internal.GarminTokenStore
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * Base class for integration tests that target only the Garmin Connect HTTP client.
 *
 * Provides its own WireMock server and Spring application context — independent of
 * [com.cyclingcoach.AbstractApplicationIntegrationTest]. Tests that extend this class
 * do not carry the full application's database-cleanup overhead.
 *
 * Auth stubs (SSO login + DI token exchange) are set up via [GarminConnectWireMockHelper]
 * before each test.
 */
@SpringBootTest
@ActiveProfiles("test")
abstract class AbstractGarminConnectTest {

    @Autowired
    lateinit var garminConnect: GarminConnect

    @Autowired
    lateinit var garminTokenStore: GarminTokenStore

    @BeforeEach
    fun resetConnectState() {
        garminTokenStore.deleteAll()
        wireMock.resetAll()
        GarminConnectWireMockHelper.stubAuthFlow(wireMock)
    }

    companion object {
        val wireMock: WireMockServer =
            WireMockServer(wireMockConfig().dynamicPort()).also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun garminProperties(registry: DynamicPropertyRegistry) {
            registry.add("garmin.email") { "test@example.com" }
            registry.add("garmin.password") { "test-password" }
            registry.add("garmin.connect.client.sso-base-url") { "http://localhost:${wireMock.port()}" }
            registry.add("garmin.connect.client.di-auth-base-url") { "http://localhost:${wireMock.port()}" }
            registry.add("garmin.connect.client.api-base-url") { "http://localhost:${wireMock.port()}" }
            GarminConnectWireMockHelper.stubAuthFlow(wireMock)
        }
    }
}
