package com.cyclingcoach.ride

import com.cyclingcoach.activity.ActivityRepository
import com.cyclingcoach.ftp.FtpEstimationService
import com.cyclingcoach.ftp.RidePowerSample
import com.cyclingcoach.user.UserProfileRepository
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

    private val activityRepository: ActivityRepository = mockk()
    private val rideRepository: RideRepository = mockk()
    private val userProfileRepository: UserProfileRepository = mockk()
    private val ftpEstimationService: FtpEstimationService = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()
    private val parser: ActivityFileParser = TcxActivityFileParser()

    private val task = RideComputeService(
        activityRepository, rideRepository, userProfileRepository, ftpEstimationService, parser, eventPublisher
    )

    private val fixtureDate = LocalDate.parse("2026-05-07")

    private fun loadFixtureTcx(): String =
        RideComputeServiceTest::class.java.getResourceAsStream("/fixtures/garmin/activity_22801381040.tcx")!!
            .bufferedReader().readText()

    @Test
    fun `compute saves ride and publishes RideCalculatedEvent`() {
        val tcx = loadFixtureTcx()
        every { rideRepository.existsByActivityId(1L) } returns false
        every { activityRepository.findRawTcxById(1L) } returns tcx
        every { userProfileRepository.findCurrentFtp() } returns 200.0
        every { userProfileRepository.findCurrentWeightKg() } returns 70.0
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
    fun `compute skips when ride already exists`() {
        every { rideRepository.existsByActivityId(1L) } returns true

        task.compute(1L, fixtureDate)

        verify(exactly = 0) { rideRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `compute does nothing when rawTcx is null`() {
        every { rideRepository.existsByActivityId(1L) } returns false
        every { activityRepository.findRawTcxById(1L) } returns null

        task.compute(1L, fixtureDate)

        verify(exactly = 0) { rideRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `compute stores null tss and intensityFactor when no FTP is set`() {
        val tcx = loadFixtureTcx()
        every { rideRepository.existsByActivityId(1L) } returns false
        every { activityRepository.findRawTcxById(1L) } returns tcx
        every { userProfileRepository.findCurrentFtp() } returns null
        every { rideRepository.findPowerSamplesBefore(any()) } returns emptyList<RidePowerSample>()
        every { ftpEstimationService.estimate(any()) } returns null
        every { userProfileRepository.findCurrentWeightKg() } returns null
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
        val tcx = loadFixtureTcx()
        every { rideRepository.existsByActivityId(1L) } returns false
        every { activityRepository.findRawTcxById(1L) } returns tcx
        every { userProfileRepository.findCurrentFtp() } returns null
        every { rideRepository.findPowerSamplesBefore(any()) } returns emptyList<RidePowerSample>()
        every { ftpEstimationService.estimate(any()) } returns 250.0
        every { userProfileRepository.findCurrentWeightKg() } returns null
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
        val tcx = loadFixtureTcx()
        every { rideRepository.existsByActivityId(10L) } returns false
        every { rideRepository.existsByActivityId(20L) } returns false
        every { activityRepository.findRawTcxById(any()) } returns tcx
        every { userProfileRepository.findCurrentFtp() } returns null
        every { rideRepository.findPowerSamplesBefore(any()) } returns emptyList<RidePowerSample>()
        every { ftpEstimationService.estimate(any()) } returns null
        every { userProfileRepository.findCurrentWeightKg() } returns null
        every { rideRepository.save(any()) } returns 99L
        justRun { eventPublisher.publishEvent(any<Any>()) }

        task.compute(10L, null)
        task.compute(20L, null)

        verify(exactly = 2) { rideRepository.save(any()) }
    }
}
