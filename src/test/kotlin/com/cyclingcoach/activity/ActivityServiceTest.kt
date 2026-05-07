package com.cyclingcoach.activity

import com.cyclingcoach.AbstractApplicationIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents

@Tag("integration")
@RecordApplicationEvents
class ActivityServiceTest : AbstractApplicationIntegrationTest() {
    @Autowired
    private lateinit var activityService: ActivityService

    @Autowired
    private lateinit var applicationEvents: ApplicationEvents

    @Test
    fun `storeAll saves activity to database`() {
        val input = ActivityInput("ext-1", "Morning Ride", "2024-06-01 08:00:00", "<tcx/>")

        activityService.storeAll(listOf(input))

        assertThat(activityRepository.existsByExternalId("ext-1")).isTrue()
    }

    @Test
    fun `storeAll fires ActivityStoredEvent with correct id and date`() {
        val input = ActivityInput("ext-1", "Morning Ride", "2024-06-01 08:00:00", "<tcx/>")

        activityService.storeAll(listOf(input))

        val events = applicationEvents.stream(ActivityStoredEvent::class.java).toList()
        assertThat(events).hasSize(1)
        assertThat(events[0].date.toString()).isEqualTo("2024-06-01")
    }

    @Test
    fun `storeAll does not fire event for duplicate activity`() {
        val input = ActivityInput("ext-dupe", "Ride", "2024-06-02 09:00:00", "<tcx/>")
        activityService.storeAll(listOf(input))
        activityService.storeAll(listOf(input))

        val events = applicationEvents.stream(ActivityStoredEvent::class.java).toList()
        assertThat(events).hasSize(1)
    }

    @Test
    fun `storeAll fires events in insertion order`() {
        val inputs =
            listOf(
                ActivityInput("ext-1", "Ride 1", "2024-06-01 08:00:00", "<tcx/>"),
                ActivityInput("ext-2", "Ride 2", "2024-06-02 08:00:00", "<tcx/>"),
                ActivityInput("ext-3", "Ride 3", "2024-06-03 08:00:00", "<tcx/>"),
            )

        activityService.storeAll(inputs)

        val events = applicationEvents.stream(ActivityStoredEvent::class.java).toList()
        assertThat(events.map { it.date.toString() })
            .containsExactly("2024-06-01", "2024-06-02", "2024-06-03")
    }
}
