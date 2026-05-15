package com.cyclingcoach

import com.cyclingcoach.garmin.activity.GarminActivityRepository
import com.cyclingcoach.garmin.activity.GarminActivitySyncCursorRepository
import com.cyclingcoach.garmin.connect.GarminConnectWireMockHelper
import com.cyclingcoach.garmin.connect.weight.GarminWeightSyncCursorRepository
import com.cyclingcoach.garmin.internal.GarminTokenStore
import com.cyclingcoach.generated.jooq.tables.references.DAILY_RECOMMENDATION
import com.cyclingcoach.generated.jooq.tables.references.FTP_TEST
import com.cyclingcoach.generated.jooq.tables.references.GARMIN_ACTIVITY
import com.cyclingcoach.generated.jooq.tables.references.GARMIN_ACTIVITY_SYNC_CURSOR
import com.cyclingcoach.generated.jooq.tables.references.GARMIN_TOKEN
import com.cyclingcoach.generated.jooq.tables.references.GARMIN_WEIGHT
import com.cyclingcoach.generated.jooq.tables.references.GARMIN_WEIGHT_SYNC_CURSOR
import com.cyclingcoach.generated.jooq.tables.references.RIDE
import com.cyclingcoach.generated.jooq.tables.references.TRAINING_LOAD
import com.cyclingcoach.generated.jooq.tables.references.USER_WEIGHT
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@IntegrationTest
abstract class AbstractApplicationIntegrationTest {
    @Autowired
    lateinit var dsl: DSLContext

    @Autowired
    lateinit var garminActivityRepository: GarminActivityRepository

    @Autowired
    lateinit var garminTokenStore: GarminTokenStore

    @Autowired
    lateinit var syncCursorRepository: GarminActivitySyncCursorRepository

    @Autowired
    lateinit var weightSyncCursorRepository: GarminWeightSyncCursorRepository

    @BeforeEach
    fun resetState() {
        dsl.deleteFrom(DAILY_RECOMMENDATION).execute()
        dsl.deleteFrom(TRAINING_LOAD).execute()
        dsl.deleteFrom(RIDE).execute()
        dsl.deleteFrom(GARMIN_ACTIVITY).execute()
        dsl.deleteFrom(FTP_TEST).execute()
        dsl.deleteFrom(USER_WEIGHT).execute()
        dsl.deleteFrom(GARMIN_WEIGHT).execute()
        dsl.deleteFrom(GARMIN_TOKEN).execute()
        dsl.deleteFrom(GARMIN_ACTIVITY_SYNC_CURSOR).execute()
        dsl.deleteFrom(GARMIN_WEIGHT_SYNC_CURSOR).execute()
        wireMock.resetAll()
        openMeteoWireMock.resetAll()
        stubGarminAuthFlow()
    }

    companion object {
        val wireMock: WireMockServer =
            WireMockServer(wireMockConfig().dynamicPort()).also { it.start() }

        val openMeteoWireMock: WireMockServer =
            WireMockServer(wireMockConfig().dynamicPort()).also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("garmin.email") { "test@example.com" }
            registry.add("garmin.password") { "test-password" }
            registry.add("garmin.connect.client.sso-base-url") { "http://localhost:${wireMock.port()}" }
            registry.add("garmin.connect.client.di-auth-base-url") { "http://localhost:${wireMock.port()}" }
            registry.add("garmin.connect.client.api-base-url") { "http://localhost:${wireMock.port()}" }
            registry.add("garmin.connect.activity.page-size") { "100" }
            registry.add("weather.open-meteo.base-url") { "http://localhost:${openMeteoWireMock.port()}" }
            stubGarminAuthFlow()
        }

        fun stubGarminAuthFlow() {
            GarminConnectWireMockHelper.stubAuthFlow(wireMock)
        }

        fun stubForecast(lat: Double = 47.376, lon: Double = 8.541) {
            openMeteoWireMock.stubFor(
                WireMock.get(WireMock.urlPathEqualTo("/v1/forecast"))
                    .withQueryParam("latitude", WireMock.equalTo(lat.toString()))
                    .withQueryParam("longitude", WireMock.equalTo(lon.toString()))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(readFixture("/fixtures/weather/open_meteo_forecast.json")),
                    ),
            )
        }
    }
}
