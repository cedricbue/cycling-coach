package com.cyclingcoach

import com.cyclingcoach.garmin.activity.GarminActivityRepository
import com.cyclingcoach.garmin.activity.GarminActivitySyncCursorRepository
import com.cyclingcoach.garmin.connect.GarminConnectWireMockHelper
import com.cyclingcoach.garmin.connect.weight.GarminWeightSyncCursorRepository
import com.cyclingcoach.garmin.internal.GarminTokenStore
import com.cyclingcoach.generated.jooq.tables.references.BIKE
import com.cyclingcoach.generated.jooq.tables.references.FTP_TEST
import com.cyclingcoach.generated.jooq.tables.references.GARMIN_ACTIVITY
import com.cyclingcoach.generated.jooq.tables.references.GARMIN_ACTIVITY_SYNC_CURSOR
import com.cyclingcoach.generated.jooq.tables.references.GARMIN_TOKEN
import com.cyclingcoach.generated.jooq.tables.references.GARMIN_WEIGHT
import com.cyclingcoach.generated.jooq.tables.references.GARMIN_WEIGHT_SYNC_CURSOR
import com.cyclingcoach.generated.jooq.tables.references.GOAL_EVENT
import com.cyclingcoach.generated.jooq.tables.references.NUTRITION_PLAN
import com.cyclingcoach.generated.jooq.tables.references.PLANNED_WORKOUT
import com.cyclingcoach.generated.jooq.tables.references.RIDE
import com.cyclingcoach.generated.jooq.tables.references.TRAINING_LOAD
import com.cyclingcoach.generated.jooq.tables.references.TRAINING_PLAN
import com.cyclingcoach.generated.jooq.tables.references.TRAINING_WEEK
import com.cyclingcoach.generated.jooq.tables.references.USER_WEIGHT
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest
@ActiveProfiles("test")
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
        dsl.deleteFrom(NUTRITION_PLAN).execute()
        dsl.deleteFrom(PLANNED_WORKOUT).execute()
        dsl.deleteFrom(TRAINING_WEEK).execute()
        dsl.deleteFrom(TRAINING_PLAN).execute()
        dsl.deleteFrom(GOAL_EVENT).execute()
        dsl.deleteFrom(TRAINING_LOAD).execute()
        dsl.deleteFrom(RIDE).execute()
        dsl.deleteFrom(GARMIN_ACTIVITY).execute()
        dsl.deleteFrom(FTP_TEST).execute()
        dsl.deleteFrom(USER_WEIGHT).execute()
        dsl.deleteFrom(GARMIN_WEIGHT).execute()
        dsl.deleteFrom(BIKE).execute()
        dsl.deleteFrom(GARMIN_TOKEN).execute()
        dsl.deleteFrom(GARMIN_ACTIVITY_SYNC_CURSOR).execute()
        dsl.deleteFrom(GARMIN_WEIGHT_SYNC_CURSOR).execute()
        wireMock.resetAll()
        stubGarminAuthFlow()
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
            registry.add("garmin.connect.activity.page-size") { "100" }
            stubGarminAuthFlow()
        }

        fun stubGarminAuthFlow() {
            GarminConnectWireMockHelper.stubAuthFlow(wireMock)
        }
    }
}
