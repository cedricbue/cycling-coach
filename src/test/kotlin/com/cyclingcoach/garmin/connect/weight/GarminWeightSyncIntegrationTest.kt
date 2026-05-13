package com.cyclingcoach.garmin.connect.weight

import com.cyclingcoach.AbstractApplicationIntegrationTest
import com.cyclingcoach.garmin.connect.client.GarminConnect
import com.cyclingcoach.generated.jooq.tables.references.GARMIN_WEIGHT
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import java.time.LocalDate

@Tag("integration")
@RecordApplicationEvents
class GarminWeightSyncIntegrationTest : AbstractApplicationIntegrationTest() {
    @Autowired
    private lateinit var garminConnect: GarminConnect

    @Autowired
    private lateinit var garminWeightSyncService: GarminWeightSyncService

    @Autowired
    lateinit var applicationEvents: ApplicationEvents

    private val threeEntriesJson: String by lazy {
        javaClass
            .getResourceAsStream("/fixtures/garmin/weight_three_entries.json")!!
            .bufferedReader()
            .readText()
    }

    @BeforeEach
    fun login() {
        garminConnect.login("test@example.com", "test-password")
    }

    @Test
    fun `weight sync stores all entries in garmin_weight`() {
        stubWeightEndpoint(threeEntriesJson)

        garminWeightSyncService.sync().get()

        val externalIds =
            dsl
                .select(GARMIN_WEIGHT.EXTERNAL_ID)
                .from(GARMIN_WEIGHT)
                .fetch(GARMIN_WEIGHT.EXTERNAL_ID)
                .toSet()
        assertThat(externalIds).containsExactlyInAnyOrder(
            "1778271913152",
            "1778083589703",
            "1777878118761",
        )
    }

    @Test
    fun `weight sync stores raw json for each entry`() {
        stubWeightEndpoint(threeEntriesJson)

        garminWeightSyncService.sync().get()

        val rawJsonEntries =
            dsl
                .select(GARMIN_WEIGHT.RAW_JSON)
                .from(GARMIN_WEIGHT)
                .fetch(GARMIN_WEIGHT.RAW_JSON)
        assertThat(rawJsonEntries).hasSize(3)
        rawJsonEntries.forEach { json -> assertThat(json).isNotBlank() }
    }

    @Test
    fun `weight sync is idempotent on re-sync`() {
        stubWeightEndpoint(threeEntriesJson)

        garminWeightSyncService.sync().get()
        garminWeightSyncService.sync().get()

        val count =
            dsl
                .selectCount()
                .from(GARMIN_WEIGHT)
                .fetchOne(0, Int::class.java)
        assertThat(count).isEqualTo(3)
    }

    @Test
    fun `weight sync writes cursor to today`() {
        stubWeightEndpoint(threeEntriesJson)

        garminWeightSyncService.sync().get()

        assertThat(weightSyncCursorRepository.findSince()).isEqualTo(LocalDate.now())
    }

    @Test
    fun `weight sync fires GarminWeightStoredEvent with all entries`() {
        stubWeightEndpoint(threeEntriesJson)

        garminWeightSyncService.sync().get()

        val events = applicationEvents.stream(GarminWeightStoredEvent::class.java).toList()
        assertThat(events).hasSize(1)

        val entries = events[0].entries
        assertThat(entries).hasSize(3)
        assertThat(entries.map { it.date }).containsExactlyInAnyOrder(
            LocalDate.of(2026, 5, 8),
            LocalDate.of(2026, 5, 6),
            LocalDate.of(2026, 5, 4),
        )
    }

    private fun stubWeightEndpoint(body: String) {
        wireMock.stubFor(
            get(urlPathEqualTo("/weight-service/weight/dateRange"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body),
                ),
        )
    }
}
