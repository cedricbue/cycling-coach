package com.cyclingcoach

import com.cyclingcoach.garmin.GarminSyncService
import com.cyclingcoach.generated.jooq.tables.references.RIDE
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration

/**
 * Verifies the FTP backfill scenario that caused the production race condition:
 *
 * 1. Normal rides are imported and computed without any FTP → null TSS.
 * 2. A subsequent sync delivers an FTP test ride.
 * 3. FTP detection triggers a backfill from the FTP test date onward.
 * 4. Rides that were already persisted with null TSS (because FTP didn't exist yet)
 *    must receive correct TSS and IF after the backfill completes.
 */
@Tag("integration")
class FtpBackfillIntegrationTest : AbstractApplicationIntegrationTest() {
    @Autowired
    private lateinit var garminSyncService: GarminSyncService

    private val normalRideJson by lazy {
        javaClass
            .getResourceAsStream("/fixtures/garmin/activity_22801381040.json")!!
            .bufferedReader()
            .readText()
    }

    private val normalRideTcx by lazy {
        javaClass
            .getResourceAsStream("/fixtures/garmin/activity_22801381040.tcx")!!
            .bufferedReader()
            .readText()
    }

    private val ftpTestJson by lazy {
        javaClass
            .getResourceAsStream("/fixtures/garmin/activity_ftp_test_20260419.json")!!
            .bufferedReader()
            .readText()
    }

    // ---- helpers ----

    private fun stubActivityList(vararg jsonBodies: String) {
        wireMock.stubFor(
            get(urlPathMatching("/activitylist-service/activities/search/activities"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[${jsonBodies.joinToString(",")}]"),
                ),
        )
    }

    private fun stubTcx(
        activityId: Long,
        body: String,
    ) {
        wireMock.stubFor(
            get(urlPathEqualTo("/download-service/export/tcx/activity/$activityId"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody(body),
                ),
        )
    }

    private fun stubWeightEmpty() {
        wireMock.stubFor(
            get(urlPathMatching("/weight-service/weight/dateRange"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]"),
                ),
        )
    }

    // ---- test ----

    @Test
    fun `FTP test detected after rides are imported backfills TSS for rides after FTP test date`() {
        stubWeightEmpty()

        // --- Phase 1: import only the normal ride (2026-05-07, no FTP in DB) ---
        stubActivityList(normalRideJson)
        stubTcx(22801381040L, normalRideTcx)

        garminSyncService.syncAll()

        // Wait for the normal ride row to appear
        await().atMost(Duration.ofSeconds(10)).until {
            dsl.fetchCount(RIDE, RIDE.EXTERNAL_ID.eq("22801381040")) > 0
        }

        val rideAfterFirstSync = dsl.selectFrom(RIDE).where(RIDE.EXTERNAL_ID.eq("22801381040")).fetchOne()!!
        assertThat(rideAfterFirstSync[RIDE.TSS]).isNull()
        assertThat(rideAfterFirstSync[RIDE.INTENSITY_FACTOR]).isNull()

        // --- Phase 2: add the FTP test ride (2026-04-19, BEFORE the normal ride) ---
        stubActivityList(normalRideJson, ftpTestJson)
        stubTcx(22901000001L, normalRideTcx) // reuse any valid TCX; only raw_json is used for metrics

        garminSyncService.syncAll()

        // FTP test detected (name contains "ftp"), ftp_test row saved, backfill runs from
        // 2026-04-19 onward — covering the normal ride on 2026-05-07.
        // Wait until the normal ride has TSS (backfill complete).
        await().atMost(Duration.ofSeconds(15)).until {
            dsl
                .select(RIDE.TSS)
                .from(RIDE)
                .where(RIDE.EXTERNAL_ID.eq("22801381040"))
                .fetchOne(RIDE.TSS) != null
        }

        val rideAfterBackfill = dsl.selectFrom(RIDE).where(RIDE.EXTERNAL_ID.eq("22801381040")).fetchOne()!!

        // TSS and IF must now be computed (FTP ≈ 241W from 254W × 0.95)
        assertThat(rideAfterBackfill[RIDE.TSS]!!.toDouble()).isGreaterThan(0.0)
        assertThat(rideAfterBackfill[RIDE.INTENSITY_FACTOR]!!.toDouble()).isGreaterThan(0.0)
        assertThat(rideAfterBackfill[RIDE.FTP]!!.toDouble()).isCloseTo(241.0, org.assertj.core.api.Assertions.within(5.0))

        // The FTP test ride itself must also have TSS
        val ftpTestRide = dsl.selectFrom(RIDE).where(RIDE.EXTERNAL_ID.eq("22901000001")).fetchOne()!!
        assertThat(ftpTestRide[RIDE.TSS]!!.toDouble()).isGreaterThan(0.0)
    }
}
