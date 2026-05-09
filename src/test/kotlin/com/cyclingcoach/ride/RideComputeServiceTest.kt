package com.cyclingcoach.ride

import com.cyclingcoach.garmin.activity.GarminActivityService
import com.cyclingcoach.ftp.FtpEstimationService
import com.cyclingcoach.ftp.RidePowerSample
import com.cyclingcoach.user.UserProfileService

import tools.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDate

@Tag("unit")
class RideComputeServiceTest {

    private val garminActivityService: GarminActivityService = mockk()
    private val rideRepository: RideRepository = mockk()
    private val userProfileService: UserProfileService = mockk()
    
    private val ftpEstimationService: FtpEstimationService = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()
    private val objectMapper = ObjectMapper()

    private val task = RideComputeService(
        garminActivityService, rideRepository, userProfileService, ftpEstimationService, eventPublisher, objectMapper
    )

    private val fixtureDate = LocalDate.parse("2026-05-07")

    private fun loadFixtureJson(): String =
        RideComputeServiceTest::class.java.getResourceAsStream("/fixtures/garmin/activity_22801381040.json")!!
            .bufferedReader().readText()

    @Test
    fun `compute saves ride and publishes RideCalculatedEvent`() {
        every { garminActivityService.findRawJsonById(1L) } returns loadFixtureJson()
        every { garminActivityService.findExternalIdById(1L) } returns "22801381040"
        every { userProfileService.findCurrentFtp() } returns 200.0
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
        every { userProfileService.findCurrentFtp() } returns 200.0
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
    fun `compute stores null tss and intensityFactor when no FTP is set`() {
        every { garminActivityService.findRawJsonById(1L) } returns loadFixtureJson()
        every { garminActivityService.findExternalIdById(1L) } returns "22801381040"
        every { userProfileService.findCurrentFtp() } returns null
        every { rideRepository.findPowerSamplesBefore(any()) } returns emptyList<RidePowerSample>()
        every { ftpEstimationService.estimate(any()) } returns null
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
    fun `compute uses estimated FTP when user profile FTP is not set`() {
        every { garminActivityService.findRawJsonById(1L) } returns loadFixtureJson()
        every { garminActivityService.findExternalIdById(1L) } returns "22801381040"
        every { userProfileService.findCurrentFtp() } returns null
        every { rideRepository.findPowerSamplesBefore(any()) } returns emptyList<RidePowerSample>()
        every { ftpEstimationService.estimate(any()) } returns 250.0
        every { userProfileService.findLatestWeightKg() } returns null
        val savedInputSlot = slot<RideInput>()
        every { rideRepository.save(capture(savedInputSlot)) } returns 1L
        justRun { eventPublisher.publishEvent(any<Any>()) }

        task.compute(1L, fixtureDate)

        assertThat(savedInputSlot.captured.ftp).isEqualTo(250.0)
        assertThat(savedInputSlot.captured.tss).isNotNull
        assertThat(savedInputSlot.captured.intensityFactor).isNotNull
    }

    @Test
    fun `compute processes multiple activities independently`() {
        every { garminActivityService.findRawJsonById(any()) } returns loadFixtureJson()
        every { garminActivityService.findExternalIdById(any()) } returnsMany listOf("ext-10", "ext-20")
        every { userProfileService.findCurrentFtp() } returns null
        every { rideRepository.findPowerSamplesBefore(any()) } returns emptyList<RidePowerSample>()
        every { ftpEstimationService.estimate(any()) } returns null
        every { userProfileService.findLatestWeightKg() } returns null
        every { rideRepository.save(any()) } returns 99L
        justRun { eventPublisher.publishEvent(any<Any>()) }

        task.compute(10L, null)
        task.compute(20L, null)

        verify(exactly = 2) { rideRepository.save(any()) }
    }
}
