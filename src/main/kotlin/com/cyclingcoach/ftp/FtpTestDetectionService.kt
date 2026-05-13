package com.cyclingcoach.ftp

import com.cyclingcoach.ride.RideCalculatedEvent
import com.cyclingcoach.ride.RideMetrics
import com.cyclingcoach.ride.RideService
import com.cyclingcoach.user.UserProfileService
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Detects whether a completed ride was an FTP test, calculates the resulting FTP,
 * validates it, and — if valid — records it in ftp_test and publishes FtpTestDetectedEvent.
 *
 * See docs/knowledge/ftp-test-detection.md for the full algorithm specification.
 */
@Service
class FtpTestDetectionService(
    private val rideService: RideService,
    private val userProfileService: UserProfileService,
    private val ftpTestRepository: FtpTestRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun detectFtpTest(event: RideCalculatedEvent) {
        val activityName = rideService.findNameByRideId(event.rideId) ?: return
        if (!isFtpTestName(activityName)) return

        if (ftpTestRepository.existsByRideId(event.rideId)) {
            log.debug("FTP test already recorded for rideId={} — skipping re-detection", event.rideId)
            return
        }

        val metrics = rideService.findMetricsById(event.rideId)
        if (metrics == null) {
            log.warn("No ride metrics found for rideId={} — skipping FTP detection", event.rideId)
            return
        }

        val testType = classifyTestType(activityName, metrics)
        log.debug("FTP test detected for rideId={}: name='{}' type={}", event.rideId, activityName, testType)

        val weightKg = userProfileService.findWeightKgAt(event.date)

        val calculatedFtp =
            calculateFtp(testType, metrics) ?: run {
                log.info(
                    "FTP test rideId={} type={}: insufficient power data — saving to ftp_test without FTP update",
                    event.rideId,
                    testType,
                )
                ftpTestRepository.save(
                    NewFtpTest(
                        event.rideId,
                        event.date,
                        0.0,
                        testType,
                        "SKIPPED: insufficient power data",
                        weightKg,
                    ),
                )
                return
            }

        val (validatedFtp, notes) = validate(calculatedFtp, metrics, ftpTestRepository.findLatestBefore(event.date)?.ftpValue)

        ftpTestRepository.save(
            NewFtpTest(
                event.rideId,
                event.date,
                validatedFtp ?: calculatedFtp,
                testType,
                notes,
                weightKg,
            ),
        )

        if (validatedFtp == null) {
            log.info(
                "FTP test rideId={} type={} calculatedFtp={}W REJECTED ({})",
                event.rideId,
                testType,
                calculatedFtp.roundToInt(),
                notes,
            )
            return
        }

        log.info(
            "FTP test rideId={} type={} ftpValue={}W{}",
            event.rideId,
            testType,
            validatedFtp.roundToInt(),
            if (notes != null) " [$notes]" else "",
        )
        eventPublisher.publishEvent(FtpTestDetectedEvent(event.rideId, event.date, testType, validatedFtp))
    }

    // --- name detection ---

    internal fun isFtpTestName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("ftp") || lower.contains("ramp test") || lower.contains("20 min test")
    }

    // --- test type classification ---

    internal fun classifyTestType(
        name: String,
        metrics: RideMetrics,
    ): FtpTestType {
        val lower = name.lowercase()

        val nameType =
            when {
                lower.contains("ramp") || lower.contains("map test") -> FtpTestType.RAMP_TEST

                lower.contains("60 min") || lower.contains("60min") || lower.contains("60-min") ||
                    lower.contains("one hour") || lower.contains("1 hour") ||
                    lower.contains("hour of power") || lower.contains("hour power") -> FtpTestType.SIXTY_MIN_TEST

                lower.contains("20 min") || lower.contains("20min") || lower.contains("20-min") ||
                    lower.contains("twenty min") || lower.contains("20 minute") ||
                    lower.contains("threshold test") -> FtpTestType.TWENTY_MIN_TEST

                else -> null // generic "ftp" — resolve via profile
            }

        // Confirm or override with power-profile signals
        return when (nameType) {
            FtpTestType.RAMP_TEST -> {
                if (profileMatchesRamp(metrics)) FtpTestType.RAMP_TEST else fallback(metrics)
            }

            FtpTestType.SIXTY_MIN_TEST -> {
                if (profileMatchesSixtyMin(metrics)) {
                    FtpTestType.SIXTY_MIN_TEST
                } else {
                    fallback(
                        metrics,
                    )
                }
            }

            FtpTestType.TWENTY_MIN_TEST -> {
                if (profileMatchesTwentyMin(metrics)) {
                    FtpTestType.TWENTY_MIN_TEST
                } else {
                    fallback(
                        metrics,
                    )
                }
            }

            null -> {
                fallback(metrics)
            }

            else -> {
                nameType
            }
        }
    }

    private fun profileMatchesRamp(m: RideMetrics): Boolean {
        var score = 0
        val vi = m.variabilityIndex
        val dur = m.durationSeconds
        val b1 = m.bestPower1min
        val avg = m.avgPower
        val b5 = m.bestPower5min
        val b20 = m.bestPower20min
        if (vi != null && vi > 1.08) score++
        if (dur != null && dur in 900.0..2400.0) score++
        if (b1 != null && avg != null && avg > 0 && b1 / avg > 1.40) score++
        if (b5 != null && b20 != null && b20 > 0 && b5 / b20 > 1.20) score++
        return score >= 3
    }

    private fun profileMatchesTwentyMin(m: RideMetrics): Boolean {
        var score = 0
        val vi = m.variabilityIndex
        val dur = m.durationSeconds
        val b20 = m.bestPower20min
        val avg = m.avgPower
        val b60 = m.bestPower60min
        if (dur != null && dur in 2100.0..5400.0) score++
        if (vi != null && vi in 1.01..1.08) score++
        if (b20 != null && avg != null && avg > 0 && b20 / avg in 1.05..1.25) score++
        if (b20 != null && b20 > 0 && (b60 == null || b60 == 0.0 || b20 / b60 > 1.10)) score++
        return score >= 3
    }

    private fun profileMatchesSixtyMin(m: RideMetrics): Boolean {
        val dur = m.durationSeconds
        val vi = m.variabilityIndex
        val avg = m.avgPower
        val b60 = m.bestPower60min
        return dur != null && dur > 3300 &&
            vi != null && vi < 1.05 &&
            avg != null && b60 != null && b60 > 0 && avg / b60 > 0.88
    }

    private fun fallback(m: RideMetrics): FtpTestType {
        val dur = m.durationSeconds ?: 0.0
        val vi = m.variabilityIndex ?: 0.0
        val b20 = m.bestPower20min ?: 0.0
        val b60 = m.bestPower60min ?: 0.0
        return when {
            dur > 3300 && vi < 1.05 && b60 > 0 -> FtpTestType.SIXTY_MIN_TEST
            vi > 1.08 && dur < 2700 -> FtpTestType.RAMP_TEST
            b20 > 0 && dur >= 2100 -> FtpTestType.TWENTY_MIN_TEST
            else -> FtpTestType.UNKNOWN
        }
    }

    // --- FTP formula ---

    internal fun calculateFtp(
        testType: FtpTestType,
        metrics: RideMetrics,
    ): Double? =
        when (testType) {
            FtpTestType.RAMP_TEST -> {
                metrics.bestPower1min?.takeIf { it > 0 }?.let { (it * 0.75).roundToInt().toDouble() }
            }

            FtpTestType.TWENTY_MIN_TEST -> {
                metrics.bestPower20min
                    ?.takeIf { it > 0 }
                    ?.let { (it * 0.95).roundToInt().toDouble() }
            }

            FtpTestType.SIXTY_MIN_TEST -> {
                metrics.bestPower60min?.takeIf { it > 0 }
            }

            FtpTestType.UNKNOWN, FtpTestType.ESTIMATED -> {
                null
            }
        }

    // --- validation ---

    /**
     * Returns (validatedFtp, notes):
     * - validatedFtp is non-null when the FTP should be applied; null means REJECTED.
     * - notes describes any warnings or the rejection reason.
     */
    internal fun validate(
        ftp: Double,
        metrics: RideMetrics,
        previousFtp: Double?,
    ): Pair<Double?, String?> {
        // Absolute bounds
        if (ftp < 60) return Pair(null, "REJECTED: FTP ${ftp.roundToInt()}W below minimum 60W")
        if (ftp > 550) return Pair(null, "REJECTED: FTP ${ftp.roundToInt()}W above maximum 550W")

        // Cross-signal consistency (warn only)
        val consistencyNote =
            buildString {
                val b5 = metrics.bestPower5min
                val b10 = metrics.bestPower10min
                val b60 = metrics.bestPower60min
                if (b5 != null && ftp >= b5) append("FTP≥best5min; ")
                if (b10 != null && ftp >= b10) append("FTP≥best10min; ")
                if (b60 != null && b60 > 0 && ftp < b60 * 0.85) append("FTP<85% of best60min; ")
            }.trimEnd(';', ' ').ifEmpty { null }

        // Change guard — warns for large deltas but always applies, since the user explicitly labelled this ride as an FTP test.
        // Only the absolute bounds above can cause a hard REJECTED.
        if (previousFtp != null && previousFtp > 0) {
            val deltaPct = (ftp - previousFtp) / previousFtp * 100
            if (abs(deltaPct) > 15) {
                val direction = if (deltaPct > 0) "increase" else "drop"
                val severity = if (abs(deltaPct) > 35) "large" else "notable"
                return Pair(
                    ftp,
                    buildNote("NEEDS_REVIEW: $severity $direction ${"%+.1f".format(deltaPct)}%", consistencyNote),
                )
            }
        }

        return Pair(ftp, consistencyNote?.let { "NEEDS_REVIEW: $it" })
    }

    private fun buildNote(
        primary: String,
        secondary: String?,
    ): String = if (secondary != null) "$primary; $secondary" else primary
}
