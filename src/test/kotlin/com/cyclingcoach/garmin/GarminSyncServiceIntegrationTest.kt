package com.cyclingcoach.garmin

import com.cyclingcoach.AbstractApplicationIntegrationTest
import com.cyclingcoach.garmin.GarminSyncService
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

    @Autowired
    private lateinit var garminActivitySyncService: com.cyclingcoach.garmin.activity.GarminActivitySyncService

    @BeforeEach
    fun authenticate() {
        garminSyncService.authenticate("test@example.com", "test-password")
    }

    @Test
    fun `sync fetches activity list and stores new activities`() {
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

        garminActivitySyncService.sync().get()

        assertThat(garminActivityRepository.existsByExternalId("12345678")).isTrue()
    }

    @Test
    fun `sync deduplicates activities already in database`() {
        stubActivityList(
            """[{"activityId":99999,"activityName":"Dupe Ride","startTimeGMT":"2024-02-01 07:00:00","activityType":{"typeKey":"cycling"}}]""",
        )
        stubTcxDownload(99999L, minimalTcx())

        garminActivitySyncService.sync().get()
        garminActivitySyncService.sync().get()

        wireMock.verify(
            1,
            getRequestedFor(urlPathEqualTo("/download-service/export/tcx/activity/99999")),
        )
    }

    @Test
    fun `sync handles empty activity list gracefully`() {
        stubActivityList("[]")

        garminActivitySyncService.sync().get()

        assertThat(garminActivityRepository.existsByExternalId("0")).isFalse()
    }

    @Test
    fun `sync handles activityList wrapper object from Garmin`() {
        stubActivityList(
            """{"activityList":[
              {"activityId":55555,"activityName":"Wrapped Ride","startTimeGMT":"2024-03-10 09:00:00","activityType":{"typeKey":"cycling"}}
            ]}""",
        )
        stubTcxDownload(55555L, minimalTcx())

        garminActivitySyncService.sync().get()

        assertThat(garminActivityRepository.existsByExternalId("55555")).isTrue()
    }

    @Test
    fun `sync re-authenticates when API returns 401 and retries`() {
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

        garminActivitySyncService.sync().get()

        assertThat(garminActivityRepository.existsByExternalId("77777")).isTrue()
    }

    @Test
    fun `sync fetches multiple pages until last page is smaller than limit`() {
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

        garminActivitySyncService.sync().get()

        assertThat(garminActivityRepository.existsByExternalId("1")).isTrue()
        assertThat(garminActivityRepository.existsByExternalId("100")).isTrue()
        assertThat(garminActivityRepository.existsByExternalId("101")).isTrue()
    }

    @Test
    fun `sync writes cursor after successful sync and uses it on next run`() {
        stubActivityList(
            """[{"activityId":200,"activityName":"Base Ride","startTimeGMT":"2024-06-01 08:00:00","activityType":{"typeKey":"cycling"}}]""",
        )
        stubTcxDownload(200L, minimalTcx())
        garminActivitySyncService.sync().get()

        assertThat(syncCursorRepository.findSince()?.toString()).isEqualTo("2024-06-01")

        wireMock.stubFor(
            get(urlPathMatching("/activitylist-service/activities/search/activities"))
                .withQueryParam("startDate", equalTo("2024-06-01"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("[]")),
        )
        garminActivitySyncService.sync().get()

        wireMock.verify(
            getRequestedFor(urlPathMatching("/activitylist-service/activities/search/activities"))
                .withQueryParam("startDate", equalTo("2024-06-01")),
        )
    }

    @Test
    fun `sync does not advance cursor when sync is aborted mid-pagination`() {
        wireMock.stubFor(
            get(urlPathMatching("/activitylist-service/activities/search/activities"))
                .withQueryParam("start", equalTo("0"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            (1..100)
                                .joinToString(",") {
                                    """{"activityId":${300 + it},"activityName":"Ride $it","startTimeGMT":"2024-07-10 08:00:00","activityType":{"typeKey":"cycling"}}"""
                                }.let { "[$it]" },
                        ),
                ),
        )
        wireMock.stubFor(
            get(urlPathMatching("/activitylist-service/activities/search/activities"))
                .withQueryParam("start", equalTo("100"))
                .willReturn(aResponse().withStatus(500)),
        )
        (301..400).forEach { stubTcxDownload(it.toLong(), minimalTcx()) }

        assertThrows<Exception> { garminActivitySyncService.sync().get() }

        assertThat(syncCursorRepository.findSince()).isNull()

        wireMock.stubFor(
            get(urlPathMatching("/activitylist-service/activities/search/activities"))
                .withQueryParam("start", equalTo("100"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """[{"activityId":401,"activityName":"Older Ride","startTimeGMT":"2024-06-01 08:00:00","activityType":{"typeKey":"cycling"}}]""",
                        ),
                ),
        )
        stubTcxDownload(401L, minimalTcx())

        garminActivitySyncService.sync().get()

        assertThat(garminActivityRepository.existsByExternalId("301")).isTrue()
        assertThat(garminActivityRepository.existsByExternalId("401")).isTrue()
        assertThat(syncCursorRepository.findSince()).isNotNull()
    }

    @Test
    fun `sync re-authenticates when session is missing then runs sync`() {
        garminTokenStore.deleteAll()
        stubActivityList("[]")

        garminActivitySyncService.sync().get()
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
