package com.cyclingcoach.garmin.connect.activity

import com.cyclingcoach.AbstractApplicationIntegrationTest
import com.cyclingcoach.garmin.activity.GarminActivityInput
import com.cyclingcoach.garmin.activity.GarminActivityService
import com.cyclingcoach.garmin.activity.GarminActivityStoredEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents

@Tag("integration")
@RecordApplicationEvents
class GarminActivityServiceTest : AbstractApplicationIntegrationTest() {
    @Autowired
    private lateinit var garminActivityService: GarminActivityService

    @Autowired
    private lateinit var applicationEvents: ApplicationEvents

    @Test
    fun `storeAll saves activity to database`() {
        val input = GarminActivityInput("ext-1", "<tcx/>")

        garminActivityService.storeAll(listOf(input))

        assertThat(garminActivityRepository.existsByExternalId("ext-1")).isTrue()
    }

    @Test
    fun `storeAll fires GarminActivityStoredEvent`() {
        val input = GarminActivityInput("ext-1", "<tcx/>")

        garminActivityService.storeAll(listOf(input))

        val events = applicationEvents.stream(GarminActivityStoredEvent::class.java).toList()
        assertThat(events).hasSize(1)
        assertThat(events[0].garminActivityId).isGreaterThan(0L)
    }

    @Test
    fun `storeAll does not fire event for duplicate activity`() {
        val input = GarminActivityInput("ext-dupe", "<tcx/>")
        garminActivityService.storeAll(listOf(input))
        garminActivityService.storeAll(listOf(input))

        val events = applicationEvents.stream(GarminActivityStoredEvent::class.java).toList()
        assertThat(events).hasSize(1)
    }

    @Test
    fun `storeAll fires events in insertion order`() {
        val inputs =
            listOf(
                GarminActivityInput("ext-1", "<tcx/>"),
                GarminActivityInput("ext-2", "<tcx/>"),
                GarminActivityInput("ext-3", "<tcx/>"),
            )

        garminActivityService.storeAll(inputs)

        val events = applicationEvents.stream(GarminActivityStoredEvent::class.java).toList()
        assertThat(events).hasSize(3)
    }
}
