package com.cyclingcoach.garmin.connect.activity

import com.cyclingcoach.AbstractApplicationIntegrationTest
import com.cyclingcoach.garmin.activity.GarminActivityStoredEvent
import com.cyclingcoach.garmin.connect.client.GarminConnect
import com.cyclingcoach.generated.jooq.tables.references.GARMIN_ACTIVITY
import com.cyclingcoach.ride.RideCalculatedEvent
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import java.time.Duration

@Tag("integration")
@RecordApplicationEvents
class GarminActivityImportIntegrationTest : AbstractApplicationIntegrationTest() {
    @Autowired
    private lateinit var garminConnect: GarminConnect

    @Autowired
    private lateinit var garminActivitySyncService: GarminActivitySyncService

    @Autowired
    lateinit var applicationEvents: ApplicationEvents

    /** Activity ID matching the fixtures in src/test/resources/fixtures/garmin/ */
    private val activityId = 22801381040L

    private val activityJson: String by lazy {
        javaClass
            .getResourceAsStream("/fixtures/garmin/activity_22801381040.json")!!
            .bufferedReader()
            .readText()
    }

    private val activityTcx: String by lazy {
        javaClass
            .getResourceAsStream("/fixtures/garmin/activity_22801381040.tcx")!!
            .bufferedReader()
            .readText()
    }

    @BeforeEach
    fun login() {
        garminConnect.login("test@example.com", "test-password")
        stubActivityList("[$activityJson]")
        stubTcxDownload(activityId, activityTcx)
    }

    @Test
    fun `activity sync with real fixture stores raw json and tcx in garmin_activity`() {
        garminActivitySyncService.sync().get()

        val row =
            dsl
                .select(
                    GARMIN_ACTIVITY.EXTERNAL_ID,
                    GARMIN_ACTIVITY.RAW_JSON,
                    GARMIN_ACTIVITY.RAW_TCX,
                ).from(GARMIN_ACTIVITY)
                .where(GARMIN_ACTIVITY.EXTERNAL_ID.eq(activityId.toString()))
                .fetchOne()

        assertThat(row).isNotNull
        assertThat(row!!.get(GARMIN_ACTIVITY.EXTERNAL_ID)).isEqualTo(activityId.toString())
        assertThat(row.get(GARMIN_ACTIVITY.RAW_JSON))
            .isNotBlank()
            .contains(""""activityId":$activityId""")
        assertThat(row.get(GARMIN_ACTIVITY.RAW_TCX))
            .isNotBlank()
            .contains("TrainingCenterDatabase")
    }

    @Test
    fun `activity sync with real fixture fires GarminActivityStoredEvent and RideCalculatedEvent`() {
        garminActivitySyncService.sync().get()

        // GarminActivityStoredEvent is published synchronously inside the sync
        val storedEvents = applicationEvents.stream(GarminActivityStoredEvent::class.java).toList()
        assertThat(storedEvents).hasSize(1)
        assertThat(storedEvents[0].externalId).isEqualTo(activityId.toString())

        // RideCalculatedEvent is published from RideEventListener which is @Async — poll until it appears
        await()
            .atMost(Duration.ofSeconds(10))
            .untilAsserted {
                val calculatedEvents = applicationEvents.stream(RideCalculatedEvent::class.java).toList()
                assertThat(calculatedEvents).hasSize(1)
                assertThat(calculatedEvents[0].activityId).isEqualTo(storedEvents[0].garminActivityId)
            }
    }

    private fun stubActivityList(jsonBody: String) {
        wireMock.stubFor(
            get(urlPathMatching("/activitylist-service/activities/search/activities"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonBody),
                ),
        )
    }

    private fun stubTcxDownload(
        id: Long,
        tcxBody: String,
    ) {
        wireMock.stubFor(
            get(urlPathEqualTo("/download-service/export/tcx/activity/$id"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody(tcxBody),
                ),
        )
    }
}
