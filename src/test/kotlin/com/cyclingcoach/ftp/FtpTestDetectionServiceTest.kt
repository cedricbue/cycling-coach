package com.cyclingcoach.ftp

import com.cyclingcoach.ride.RideCalculatedEvent
import com.cyclingcoach.ride.RideMetrics
import com.cyclingcoach.ride.RideService
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
import java.time.LocalDate
import kotlin.math.roundToInt

@Tag("unit")
class FtpTestDetectionServiceTest {

    private val rideService: RideService = mockk()
    private val userProfileService: UserProfileService = mockk()
    private val ftpTestRepository: FtpTestRepository = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()

    private val service = FtpTestDetectionService(
        rideService, userProfileService, ftpTestRepository, eventPublisher
    )

    private val date = LocalDate.parse("2026-05-08")
    private val event = RideCalculatedEvent(rideId = 1L, activityId = 10L, date = date, tss = 80.0)

    private fun rampMetrics() = RideMetrics(
        durationSeconds = 1500.0, avgPower = 150.0, variabilityIndex = 1.12,
        bestPower1min = 320.0, bestPower5min = 270.0, bestPower10min = 240.0,
        bestPower20min = 200.0, bestPower60min = null
    )

    private fun twentyMinMetrics() = RideMetrics(
        durationSeconds = 3600.0, avgPower = 190.0, variabilityIndex = 1.04,
        bestPower1min = 350.0, bestPower5min = 280.0, bestPower10min = 265.0,
        bestPower20min = 260.0, bestPower60min = null
    )

    private fun sixtyMinMetrics() = RideMetrics(
        durationSeconds = 3900.0, avgPower = 240.0, variabilityIndex = 1.02,
        bestPower1min = 320.0, bestPower5min = 270.0, bestPower10min = 255.0,
        bestPower20min = 250.0, bestPower60min = 242.0
    )

    // --- name filter ---

    @Test
    fun `skips activity whose name does not signal an FTP test`() {
        every { rideService.findNameByRideId(1L) } returns "Morning Ride"

        service.detectFtpTest(event)

        verify(exactly = 0) { rideService.findMetricsById(any()) }
        verify(exactly = 0) { ftpTestRepository.save(any(), any(), any(), any()) }
    }

    @Test
    fun `skips when activity name is null`() {
        every { rideService.findNameByRideId(1L) } returns null

        service.detectFtpTest(event)

        verify(exactly = 0) { rideService.findMetricsById(any()) }
    }

    // --- isFtpTestName ---

    @Test
    fun `isFtpTestName matches ftp case-insensitively`() {
        assertThat(service.isFtpTestName("FTP Test")).isTrue()
        assertThat(service.isFtpTestName("my ftp effort")).isTrue()
        assertThat(service.isFtpTestName("FTPtest")).isTrue()
    }

    @Test
    fun `isFtpTestName matches ramp test`() {
        assertThat(service.isFtpTestName("Ramp Test")).isTrue()
        assertThat(service.isFtpTestName("RAMP TEST")).isTrue()
    }

    @Test
    fun `isFtpTestName matches 20 min test`() {
        assertThat(service.isFtpTestName("20 min test")).isTrue()
        assertThat(service.isFtpTestName("20 Min Test effort")).isTrue()
    }

    @Test
    fun `isFtpTestName rejects unrelated names`() {
        assertThat(service.isFtpTestName("Easy Z2 Ride")).isFalse()
        assertThat(service.isFtpTestName("Intervals")).isFalse()
    }

    // --- classifyTestType ---

    @Test
    fun `classifies ramp test by name and matching profile`() {
        val type = service.classifyTestType("Ramp Test", rampMetrics())
        assertThat(type).isEqualTo(FtpTestType.RAMP_TEST)
    }

    @Test
    fun `classifies 20min test by name and matching profile`() {
        val type = service.classifyTestType("20min FTP Test", twentyMinMetrics())
        assertThat(type).isEqualTo(FtpTestType.TWENTY_MIN_TEST)
    }

    @Test
    fun `classifies 60min test by name and matching profile`() {
        val type = service.classifyTestType("60min FTP test", sixtyMinMetrics())
        assertThat(type).isEqualTo(FtpTestType.SIXTY_MIN_TEST)
    }

    @Test
    fun `falls back to TWENTY_MIN_TEST when name is generic ftp and profile matches`() {
        val type = service.classifyTestType("FTP Test", twentyMinMetrics())
        assertThat(type).isEqualTo(FtpTestType.TWENTY_MIN_TEST)
    }

    @Test
    fun `returns UNKNOWN when profile does not match any type`() {
        val ambiguousMetrics = RideMetrics(
            durationSeconds = 1800.0, avgPower = 100.0, variabilityIndex = 1.05,
            bestPower1min = 110.0, bestPower5min = null, bestPower10min = null,
            bestPower20min = null, bestPower60min = null
        )
        val type = service.classifyTestType("FTP effort", ambiguousMetrics)
        assertThat(type).isEqualTo(FtpTestType.UNKNOWN)
    }

    // --- calculateFtp ---

    @Test
    fun `calculateFtp ramp test uses best1min times 0_75`() {
        val ftp = service.calculateFtp(FtpTestType.RAMP_TEST, rampMetrics())
        assertThat(ftp).isEqualTo((320.0 * 0.75).roundToInt().toDouble())
    }

    @Test
    fun `calculateFtp 20min test uses best20min times 0_95`() {
        val ftp = service.calculateFtp(FtpTestType.TWENTY_MIN_TEST, twentyMinMetrics())
        assertThat(ftp).isEqualTo((260.0 * 0.95).roundToInt().toDouble())
    }

    @Test
    fun `calculateFtp 60min test uses best60min directly`() {
        val ftp = service.calculateFtp(FtpTestType.SIXTY_MIN_TEST, sixtyMinMetrics())
        assertThat(ftp).isEqualTo(242.0)
    }

    @Test
    fun `calculateFtp returns null for UNKNOWN`() {
        assertThat(service.calculateFtp(FtpTestType.UNKNOWN, rampMetrics())).isNull()
    }

    @Test
    fun `calculateFtp returns null when required power field is missing`() {
        val noData = RideMetrics(null, null, null, null, null, null, null, null)
        assertThat(service.calculateFtp(FtpTestType.RAMP_TEST, noData)).isNull()
        assertThat(service.calculateFtp(FtpTestType.TWENTY_MIN_TEST, noData)).isNull()
        assertThat(service.calculateFtp(FtpTestType.SIXTY_MIN_TEST, noData)).isNull()
    }

    // --- validate ---

    @Test
    fun `validate accepts normal FTP with no previous value`() {
        val (ftp, notes) = service.validate(250.0, twentyMinMetrics(), null)
        assertThat(ftp).isEqualTo(250.0)
        assertThat(notes).isNull()
    }

    @Test
    fun `validate rejects FTP below 60W`() {
        val (ftp, notes) = service.validate(55.0, twentyMinMetrics(), null)
        assertThat(ftp).isNull()
        assertThat(notes).contains("REJECTED")
    }

    @Test
    fun `validate rejects FTP above 550W`() {
        val (ftp, notes) = service.validate(600.0, twentyMinMetrics(), null)
        assertThat(ftp).isNull()
        assertThat(notes).contains("REJECTED")
    }

    @Test
    fun `validate flags large gain over 25 percent as NEEDS_REVIEW but still applies FTP`() {
        val (ftp, notes) = service.validate(330.0, twentyMinMetrics(), 250.0) // +32%
        assertThat(ftp).isEqualTo(330.0)
        assertThat(notes).contains("NEEDS_REVIEW")
    }

    @Test
    fun `validate flags large regression below minus 35 percent as NEEDS_REVIEW but still applies FTP`() {
        val (ftp, notes) = service.validate(150.0, twentyMinMetrics(), 250.0) // -40%
        assertThat(ftp).isEqualTo(150.0)
        assertThat(notes).contains("NEEDS_REVIEW")
    }

    @Test
    fun `validate flags large increase as NEEDS_REVIEW but still returns FTP`() {
        val (ftp, notes) = service.validate(295.0, twentyMinMetrics(), 250.0) // +18%
        assertThat(ftp).isEqualTo(295.0)
        assertThat(notes).contains("NEEDS_REVIEW")
    }

    @Test
    fun `validate flags large drop as NEEDS_REVIEW but still returns FTP`() {
        val (ftp, notes) = service.validate(195.0, twentyMinMetrics(), 250.0) // -22%
        assertThat(ftp).isEqualTo(195.0)
        assertThat(notes).contains("NEEDS_REVIEW")
    }

    // --- full event flow ---

    @Test
    fun `publishes FtpTestDetectedEvent for a valid 20min test`() {
        every { rideService.findNameByRideId(1L) } returns "20min FTP Test"
        every { rideService.findMetricsById(1L) } returns twentyMinMetrics()
        every { userProfileService.findCurrentFtp() } returns 240.0
        every { userProfileService.findWeightKgAt(any()) } returns null
        justRun { ftpTestRepository.save(any(), any(), any(), any(), any()) }
        val eventSlot = slot<FtpTestDetectedEvent>()
        every { eventPublisher.publishEvent(capture(eventSlot)) } returns Unit

        service.detectFtpTest(event)

        verify(exactly = 1) { eventPublisher.publishEvent(any<FtpTestDetectedEvent>()) }
        assertThat(eventSlot.captured.testType).isEqualTo(FtpTestType.TWENTY_MIN_TEST)
        assertThat(eventSlot.captured.ftpValue).isEqualTo((260.0 * 0.95).roundToInt().toDouble())
    }

    @Test
    fun `does not publish event for UNKNOWN test type`() {
        val ambiguous = RideMetrics(1800.0, 100.0, 1.05, 110.0, null, null, null, null)
        every { rideService.findNameByRideId(1L) } returns "FTP effort"
        every { rideService.findMetricsById(1L) } returns ambiguous
        every { userProfileService.findCurrentFtp() } returns null
        every { userProfileService.findWeightKgAt(any()) } returns null
        justRun { ftpTestRepository.save(any(), any(), any(), any(), any()) }

        service.detectFtpTest(event)

        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `publishes event with NEEDS_REVIEW when FTP change is very large`() {
        every { rideService.findNameByRideId(1L) } returns "Ramp Test"
        every { rideService.findMetricsById(1L) } returns rampMetrics()
        every { userProfileService.findCurrentFtp() } returns 100.0 // 320×0.75=240 → +140% → NEEDS_REVIEW
        every { userProfileService.findWeightKgAt(any()) } returns null
        justRun { ftpTestRepository.save(any(), any(), any(), any(), any()) }
        val eventSlot = slot<FtpTestDetectedEvent>()
        every { eventPublisher.publishEvent(capture(eventSlot)) } returns Unit

        service.detectFtpTest(event)

        verify(exactly = 1) { eventPublisher.publishEvent(any<FtpTestDetectedEvent>()) }
        assertThat(eventSlot.captured.ftpValue).isEqualTo((320.0 * 0.75).roundToInt().toDouble())
        verify(exactly = 1) { ftpTestRepository.save(any(), any(), any(), match { it?.contains("NEEDS_REVIEW") == true }, any()) }
    }
}
