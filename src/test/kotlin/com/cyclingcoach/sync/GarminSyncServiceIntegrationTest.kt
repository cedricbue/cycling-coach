package com.cyclingcoach.sync

import com.cyclingcoach.ApplicationContextTest
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@Tag("integration")
class GarminSyncServiceIntegrationTest : ApplicationContextTest() {

    @Autowired
    private lateinit var garminSyncService: GarminSyncService

    @BeforeEach
    fun resetState() {
        dsl.deleteFrom(DSL.table("activity")).execute()
        garminSessionRepository.deleteAll()
        // Re-authenticate so each test starts with a fresh valid session
        garminSyncService.authenticate("test@example.com", "test-password")
    }

    @Test
    fun `syncActivities fetches activity list and stores new activities`() {
        stubActivityList(
            """[
              {
                "activityId": 12345678,
                "activityName": "Morning Ride",
                "startTimeGMT": "2024-01-15 08:30:00",
                "activityType": {"typeKey": "cycling"}
              }
            ]""",
        )
        stubTcxDownload(
            12345678L,
            """<?xml version="1.0"?><TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"/>""",
        )

        garminSyncService.syncActivities()

        assertThat(activityRepository.existsByExternalId("12345678")).isTrue()
    }

    @Test
    fun `syncActivities deduplicates activities already in database`() {
        stubActivityList("""[{"activityId":99999,"activityName":"Dupe Ride","startTimeGMT":"2024-02-01 07:00:00","activityType":{"typeKey":"cycling"}}]""")
        stubTcxDownload(99999L, minimalTcx())

        garminSyncService.syncActivities()
        garminSyncService.syncActivities() // second call — same activity

        // TCX download endpoint should only have been called once
        wireMock.verify(
            1,
            getRequestedFor(urlPathEqualTo("/download-service/export/tcx/activity/99999")),
        )
    }

    @Test
    fun `syncActivities handles empty activity list gracefully`() {
        stubActivityList("[]")

        garminSyncService.syncActivities()

        // No activities in DB from this run
        assertThat(activityRepository.existsByExternalId("0")).isFalse()
    }

    @Test
    fun `syncActivities handles activityList wrapper object from Garmin`() {
        stubActivityList(
            """{"activityList":[
              {"activityId":55555,"activityName":"Wrapped Ride","startTimeGMT":"2024-03-10 09:00:00","activityType":{"typeKey":"cycling"}}
            ]}""",
        )
        stubTcxDownload(55555L, minimalTcx())

        garminSyncService.syncActivities()

        assertThat(activityRepository.existsByExternalId("55555")).isTrue()
    }

    @Test
    fun `syncActivities re-authenticates when API returns 401 and retries`() {
        // First activity list call returns 401; after re-auth it succeeds
        wireMock.stubFor(
            get(urlPathMatching("/activitylist-service/activities/search/activities"))
                .inScenario("reauth")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(401))
                .willSetStateTo("reauthed"),
        )
        wireMock.stubFor(
            get(urlPathMatching("/activitylist-service/activities/search/activities"))
                .inScenario("reauth")
                .whenScenarioStateIs("reauthed")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""[{"activityId":77777,"activityName":"After Reauth","startTimeGMT":"2024-04-01 06:00:00","activityType":{"typeKey":"cycling"}}]"""),
                ),
        )
        stubTcxDownload(77777L, minimalTcx())
        // Re-auth stubs (login + token exchange) are already registered by ApplicationContextTest

        garminSyncService.syncActivities()

        assertThat(activityRepository.existsByExternalId("77777")).isTrue()
    }

    @Test
    fun `syncActivities aborts gracefully when no valid session exists`() {
        garminSessionRepository.deleteAll()

        // Should not throw — just log and return
        garminSyncService.syncActivities()
    }

    // ── Stub helpers ─────────────────────────────────────────────────────────────

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
        activityId: Long,
        tcxBody: String,
    ) {
        wireMock.stubFor(
            get(urlPathEqualTo("/download-service/export/tcx/activity/$activityId"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody(tcxBody),
                ),
        )
    }

    private fun minimalTcx() =
        """<?xml version="1.0"?><TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"/>"""
}
