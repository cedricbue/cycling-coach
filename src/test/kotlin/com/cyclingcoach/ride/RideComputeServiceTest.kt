package com.cyclingcoach.ride

import com.cyclingcoach.ftp.FtpService
import com.cyclingcoach.garmin.activity.GarminActivityService
import com.cyclingcoach.user.UserProfileService
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate

@Tag("unit")
class RideComputeServiceTest {
    private val garminActivityService: GarminActivityService = mockk()
    private val rideRepository: RideRepository = mockk()
    private val userProfileService: UserProfileService = mockk()
    private val ftpService: FtpService = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()
    private val objectMapper = ObjectMapper()

    private val task =
        RideComputeService(
            garminActivityService,
            rideRepository,
            userProfileService,
            ftpService,
            eventPublisher,
            objectMapper,
        )

    private val fixtureDate = LocalDate.parse("2026-05-07")

    private fun loadFixtureJson(): String =
        RideComputeServiceTest::class.java
            .getResourceAsStream("/fixtures/garmin/activity_22801381040.json")!!
            .bufferedReader()
            .readText()

    @Test
    fun `compute saves ride and publishes RideCalculatedEvent`() {
        every { garminActivityService.findRawJsonById(1L) } returns loadFixtureJson()
        every { garminActivityService.findExternalIdById(1L) } returns "22801381040"
        every { ftpService.findEffectiveAt(any()) } returns 200.0
        every { userProfileService.findLatestWeightKg() } returns 70.0
        every { rideRepository.save(any()) } returns 42L
        justRun { eventPublisher.publishEvent(any<Any>()) }

        task.compute(1L, fixtureDate)

        verify(exactly = 1) { rideRepository.save(any()) }
        val eventSlot = slot<Any>()
        verify { eventPublisher.publishEvent(capture(eventSlot)) }
        val event = eventSlot.captured as RideCalculatedEvent
        assertThat(event.rideId).isEqualTo(42L)
        assertThat(event.activityId).isEqualTo(1L)
    }

    @Test
    fun `compute passes externalId to RideInput so upsert deduplicates by external_id`() {
        every { garminActivityService.findRawJsonById(184L) } returns loadFixtureJson()
        every { garminActivityService.findExternalIdById(184L) } returns "22801381040"
        every { ftpService.findEffectiveAt(any()) } returns 200.0
        every { userProfileService.findLatestWeightKg() } returns 70.0
        val savedInputSlot = slot<RideInput>()
        every { rideRepository.save(capture(savedInputSlot)) } returns 42L
        justRun { eventPublisher.publishEvent(any<Any>()) }

        task.compute(184L, fixtureDate)

        assertThat(savedInputSlot.captured.externalId).isEqualTo("22801381040")
        assertThat(savedInputSlot.captured.activityId).isEqualTo(184L)
    }

    @Test
    fun `compute skips when raw_json is null`() {
        every { garminActivityService.findRawJsonById(1L) } returns null

        task.compute(1L, fixtureDate)

        verify(exactly = 0) { rideRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `compute stores null tss and intensityFactor when no FTP test exists at ride date`() {
        every { garminActivityService.findRawJsonById(1L) } returns loadFixtureJson()
        every { garminActivityService.findExternalIdById(1L) } returns "22801381040"
        every { ftpService.findEffectiveAt(any()) } returns null
        every { userProfileService.findLatestWeightKg() } returns null
        val savedInputSlot = slot<RideInput>()
        every { rideRepository.save(capture(savedInputSlot)) } returns 1L
        justRun { eventPublisher.publishEvent(any<Any>()) }

        task.compute(1L, fixtureDate)

        assertThat(savedInputSlot.captured.tss).isNull()
        assertThat(savedInputSlot.captured.intensityFactor).isNull()
        assertThat(savedInputSlot.captured.wattsPerKg).isNull()
    }

    @Test
    fun `compute suppresses RideCalculatedEvent when publishEvent is false`() {
        every { garminActivityService.findRawJsonById(1L) } returns loadFixtureJson()
        every { garminActivityService.findExternalIdById(1L) } returns "22801381040"
        every { ftpService.findEffectiveAt(any()) } returns 250.0
        every { userProfileService.findLatestWeightKg() } returns null
        every { rideRepository.save(any()) } returns 1L

        task.compute(1L, fixtureDate, publishEvent = false)

        verify(exactly = 1) { rideRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `compute processes multiple activities independently`() {
        every { garminActivityService.findRawJsonById(any()) } returns loadFixtureJson()
        every { garminActivityService.findExternalIdById(any()) } returnsMany listOf("ext-10", "ext-20")
        every { ftpService.findEffectiveAt(any()) } returns null
        every { userProfileService.findLatestWeightKg() } returns null
        every { rideRepository.save(any()) } returns 99L
        justRun { eventPublisher.publishEvent(any<Any>()) }

        task.compute(10L, null)
        task.compute(20L, null)

        verify(exactly = 2) { rideRepository.save(any()) }
    }
}
