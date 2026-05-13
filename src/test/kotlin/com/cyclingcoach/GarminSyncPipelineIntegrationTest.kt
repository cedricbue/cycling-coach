package com.cyclingcoach

import com.cyclingcoach.garmin.GarminSyncService
import com.cyclingcoach.generated.jooq.tables.references.FTP_TEST
import com.cyclingcoach.generated.jooq.tables.references.RIDE
import com.cyclingcoach.generated.jooq.tables.references.USER_WEIGHT
import com.cyclingcoach.pmc.TrainingLoadRepository
import com.cyclingcoach.user.UserProfileService
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.LocalDate

/**
 * End-to-end integration test for the full Garmin sync pipeline.
 *
 * Triggers [GarminSyncService.syncAll] using real fixtures and verifies every downstream
 * domain is populated correctly:
 *  - Ride metrics (distance, duration, HR, NP, TSS)
 *  - PMC training load (CTL, ATL, TSB) for the ride date
 *  - User weight entries synced from Garmin
 */
@Tag("integration")
class GarminSyncPipelineIntegrationTest : AbstractApplicationIntegrationTest() {
    @Autowired
    private lateinit var garminSyncService: GarminSyncService

    @Autowired
    private lateinit var trainingLoadRepository: TrainingLoadRepository

    @Autowired
    private lateinit var userProfileService: UserProfileService

    private val activityJson by lazy {
        javaClass
            .getResourceAsStream("/fixtures/garmin/activity_22801381040.json")!!
            .bufferedReader()
            .readText()
    }

    private val activityTcx by lazy {
        javaClass
            .getResourceAsStream("/fixtures/garmin/activity_22801381040.tcx")!!
            .bufferedReader()
            .readText()
    }

    private val weightJson by lazy {
        javaClass
            .getResourceAsStream("/fixtures/garmin/weight_three_entries.json")!!
            .bufferedReader()
            .readText()
    }

    @BeforeEach
    fun setup() {
        // Seed an FTP test so rides on or after 2020-01-01 have effective FTP = 250W
        dsl.deleteFrom(FTP_TEST).execute()
        dsl
            .insertInto(FTP_TEST)
            .set(FTP_TEST.DATE, "2020-01-01")
            .set(FTP_TEST.FTP_VALUE, 250f)
            .set(FTP_TEST.TEST_TYPE, "TWENTY_MIN_TEST")
            .execute()

        // Stub activity list — single real activity
        wireMock.stubFor(
            get(urlPathMatching("/activitylist-service/activities/search/activities"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[$activityJson]"),
                ),
        )

        // Stub TCX download for the real activity
        wireMock.stubFor(
            get(urlPathEqualTo("/download-service/export/tcx/activity/22801381040"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody(activityTcx),
                ),
        )

        // Stub weight endpoint (date range is flexible)
        wireMock.stubFor(
            get(urlPathMatching("/weight-service/weight/dateRange"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(weightJson),
                ),
        )
    }

    @Test
    fun `full sync stores ride with correct metrics`() {
        garminSyncService.syncAll()

        await()
            .atMost(Duration.ofSeconds(10))
            .until {
                dsl
                    .selectCount()
                    .from(RIDE)
                    .where(RIDE.EXTERNAL_ID.eq("22801381040"))
                    .fetchOne(0, Int::class.java)!! > 0
            }

        val ride = dsl.selectFrom(RIDE).where(RIDE.EXTERNAL_ID.eq("22801381040")).fetchOne()!!

        assertThat(ride[RIDE.DISTANCE]!!.toDouble()).isCloseTo(29_813.0, within(500.0))
        assertThat(ride[RIDE.DURATION]!!.toDouble()).isCloseTo(3_634.0, within(10.0))
        assertThat(ride[RIDE.AVG_HR]!!.toDouble()).isCloseTo(127.0, within(2.0))
        assertThat(ride[RIDE.NORMALIZED_POWER]!!.toDouble()).isCloseTo(122.0, within(5.0))
        assertThat(ride[RIDE.TSS]!!.toDouble()).isGreaterThan(0.0)
    }

    @Test
    fun `full sync recalculates pmc training load for ride date`() {
        garminSyncService.syncAll()

        val rideDate = LocalDate.parse("2026-05-07")

        await()
            .atMost(Duration.ofSeconds(10))
            .until { trainingLoadRepository.findByDate(rideDate) != null }

        val tl = trainingLoadRepository.findByDate(rideDate)!!

        assertThat(tl.tss).isGreaterThan(0.0)
        assertThat(tl.ctl).isGreaterThan(0.0)
        assertThat(tl.atl).isGreaterThan(0.0)
    }

    @Test
    fun `full sync propagates garmin weight entries to user_weight`() {
        garminSyncService.syncAll()

        await()
            .atMost(Duration.ofSeconds(5))
            .until {
                dsl
                    .selectCount()
                    .from(USER_WEIGHT)
                    .fetchOne(0, Int::class.java)!! >= 3
            }

        assertThat(userProfileService.findLatestWeightKg()).isCloseTo(88.0, within(0.1))
    }
}
