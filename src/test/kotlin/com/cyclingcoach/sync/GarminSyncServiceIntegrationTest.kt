package com.cyclingcoach.sync

import com.cyclingcoach.AbstractApplicationIntegrationTest
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired

@Tag("integration")
class GarminSyncServiceIntegrationTest : AbstractApplicationIntegrationTest() {
    @Autowired
    private lateinit var garminSyncService: GarminSyncService

    @BeforeEach
    fun authenticate() {
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
        stubActivityList(
            """[{"activityId":99999,"activityName":"Dupe Ride","startTimeGMT":"2024-02-01 07:00:00","activityType":{"typeKey":"cycling"}}]""",
        )
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
                        .withBody(
                            """[{"activityId":77777,"activityName":"After Reauth","startTimeGMT":"2024-04-01 06:00:00","activityType":{"typeKey":"cycling"}}]""",
                        ),
                ),
        )
        stubTcxDownload(77777L, minimalTcx())
        // Re-auth stubs (login + token exchange) are already registered by ApplicationContextTest

        garminSyncService.syncActivities()

        assertThat(activityRepository.existsByExternalId("77777")).isTrue()
    }

    @Test
    fun `syncActivities fetches multiple pages until last page is smaller than limit`() {
        val page1 =
            (1..100)
                .joinToString(",") { i ->
                    """{"activityId":$i,"activityName":"Ride $i","startTimeGMT":"2024-01-01 08:00:00","activityType":{"typeKey":"cycling"}}"""
                }.let { "[$it]" }
        val page2 =
            """[{"activityId":101,"activityName":"Last Ride","startTimeGMT":"2024-01-02 08:00:00","activityType":{"typeKey":"cycling"}}]"""

        wireMock.stubFor(
            get(urlPathMatching("/activitylist-service/activities/search/activities"))
                .withQueryParam("start", equalTo("0"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(page1)),
        )
        wireMock.stubFor(
            get(urlPathMatching("/activitylist-service/activities/search/activities"))
                .withQueryParam("start", equalTo("100"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(page2)),
        )
        (1..101).forEach { i -> stubTcxDownload(i.toLong(), minimalTcx()) }

        garminSyncService.syncActivities()

        assertThat(activityRepository.existsByExternalId("1")).isTrue()
        assertThat(activityRepository.existsByExternalId("100")).isTrue()
        assertThat(activityRepository.existsByExternalId("101")).isTrue()
    }

    @Test
    fun `syncActivities writes cursor after successful sync and uses it on next run`() {
        stubActivityList(
            """[{"activityId":200,"activityName":"Base Ride","startTimeGMT":"2024-06-01 08:00:00","activityType":{"typeKey":"cycling"}}]""",
        )
        stubTcxDownload(200L, minimalTcx())
        garminSyncService.syncActivities()

        assertThat(syncCursorRepository.findSince()?.toString()).isEqualTo("2024-06-01")

        // Second sync — cursor drives the startDate query param
        wireMock.stubFor(
            get(urlPathMatching("/activitylist-service/activities/search/activities"))
                .withQueryParam("startDate", equalTo("2024-06-01"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("[]")),
        )
        garminSyncService.syncActivities()

        wireMock.verify(
            getRequestedFor(urlPathMatching("/activitylist-service/activities/search/activities"))
                .withQueryParam("startDate", equalTo("2024-06-01")),
        )
    }

    @Test
    fun `syncActivities does not advance cursor when sync is aborted mid-pagination`() {
        // Page 1 (newest): full page of 100 → triggers pagination to page 2
        wireMock.stubFor(
            get(urlPathMatching("/activitylist-service/activities/search/activities"))
                .withQueryParam("start", equalTo("0"))
                .willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody((1..100).joinToString(",") {
                            """{"activityId":${300 + it},"activityName":"Ride $it","startTimeGMT":"2024-07-10 08:00:00","activityType":{"typeKey":"cycling"}}"""
                        }.let { "[$it]" }),
                ),
        )
        // Page 2 (older): simulate server error during fetch
        wireMock.stubFor(
            get(urlPathMatching("/activitylist-service/activities/search/activities"))
                .withQueryParam("start", equalTo("100"))
                .willReturn(aResponse().withStatus(500)),
        )
        (301..400).forEach { stubTcxDownload(it.toLong(), minimalTcx()) }

        assertThrows<Exception> { garminSyncService.syncActivities() }

        // Cursor must not have been written — next sync should retry from scratch
        assertThat(syncCursorRepository.findSince()).isNull()

        // Stub page 2 to succeed on the retry sync (older activities)
        wireMock.stubFor(
            get(urlPathMatching("/activitylist-service/activities/search/activities"))
                .withQueryParam("start", equalTo("100"))
                .willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(
                            """[{"activityId":401,"activityName":"Older Ride","startTimeGMT":"2024-06-01 08:00:00","activityType":{"typeKey":"cycling"}}]""",
                        ),
                ),
        )
        stubTcxDownload(401L, minimalTcx())

        garminSyncService.syncActivities()

        // Both the newest (already stored, deduped) and the previously-missing older ride are present
        assertThat(activityRepository.existsByExternalId("301")).isTrue()
        assertThat(activityRepository.existsByExternalId("401")).isTrue()
        assertThat(syncCursorRepository.findSince()).isNotNull()
    }

    @Test
    fun `syncActivities re-authenticates when session is missing then runs sync`() {
        garminTokenStore.deleteAll()
        stubActivityList("[]")

        // Should re-auth via stored credentials and complete without throwing
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
