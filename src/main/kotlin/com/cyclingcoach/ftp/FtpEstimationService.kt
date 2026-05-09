package com.cyclingcoach.ftp

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Estimates FTP from historical ride power data when no explicit FTP is set on the user profile.
 *
 * Algorithm (see docs/knowledge/ftp-estimation.md for full specification):
 *
 * 1. Collect best power values across eligible rides (90-day window, ≥20 min duration).
 * 2. Fit a 2-parameter Critical Power model via linear regression on work = P×t.
 *    The slope (CP) is the primary FTP estimate, weighted by R².
 * 3. Add single-point fallback estimates:
 *    - 60min × 1.00  (weight 1.00)
 *    - 20min × 0.95  (weight 0.85, Coggan)
 *    - 10min × 0.85  (weight 0.60)
 * 4. Return a weighted average of all estimates, or null if no data is available.
 */
@Service
class FtpEstimationService {

    private val log = LoggerFactory.getLogger(javaClass)

    data class EstimationResult(
        val estimatedFtp: Double,
        val confidence: Confidence,
        val methodsUsed: List<String>,
        val rSquared: Double?,
        val rideCount: Int,
    )

    enum class Confidence { HIGH, MEDIUM, LOW, INSUFFICIENT_DATA }

    /**
     * Estimates FTP from the given [samples] (rides preceding the target ride).
     * Returns null when there is not enough data to produce a reliable estimate.
     */
    fun estimate(samples: List<RidePowerSample>): Double? {
        if (samples.isEmpty()) return null

        val bests = extractBests(samples)
        val estimates = mutableListOf<WeightedEstimate>()

        val cpResult = fitCriticalPowerModel(bests)
        if (cpResult != null) estimates += cpResult

        bests.best60min?.let { estimates += WeightedEstimate(it * 1.00, 1.00, "60min_direct") }
        bests.best20min?.let { estimates += WeightedEstimate((it * 0.95).roundToInt().toDouble(), 0.85, "20min_coggan") }
        bests.best10min?.let { estimates += WeightedEstimate((it * 0.85).roundToInt().toDouble(), 0.60, "10min_estimate") }

        if (estimates.isEmpty()) return null

        val totalWeight = estimates.sumOf { it.weight }
        val weightedFtp = estimates.sumOf { it.value * it.weight } / totalWeight
        val finalFtp = weightedFtp.roundToInt().toDouble()

        val confidence = determineConfidence(estimates, cpResult)
        log.info(
            "FTP estimated at {}W ({}) from {} rides using [{}]",
            finalFtp.toInt(),
            confidence,
            samples.size,
            estimates.joinToString { it.method },
        )

        return finalFtp
    }

    // --- internals ---

    private data class Bests(
        val best5min: Double?,
        val best10min: Double?,
        val best20min: Double?,
        val best60min: Double?,
    )

    private data class WeightedEstimate(val value: Double, val weight: Double, val method: String)

    private data class CpModelResult(
        val cp: Double,
        val wPrime: Double,
        val rSquared: Double,
    )

    private fun extractBests(samples: List<RidePowerSample>): Bests =
        Bests(
            best5min  = samples.filter { it.durationSeconds >= 300  }.mapNotNull { it.bestPower5min  }.filter { it > 0 }.maxOrNull(),
            best10min = samples.filter { it.durationSeconds >= 600  }.mapNotNull { it.bestPower10min }.filter { it > 0 }.maxOrNull(),
            best20min = samples.filter { it.durationSeconds >= 1200 }.mapNotNull { it.bestPower20min }.filter { it > 0 }.maxOrNull(),
            best60min = samples.filter { it.durationSeconds >= 3600 }.mapNotNull { it.bestPower60min }.filter { it > 0 }.maxOrNull(),
        )

    /**
     * Fits the 2-parameter Critical Power model: P(t) = CP + W'/t
     * Rearranged to linear form: P×t = CP×t + W'  →  Y = m×X + b
     * Returns null if fewer than 2 data points are available or the model is implausible.
     */
    private fun fitCriticalPowerModel(bests: Bests): WeightedEstimate? {
        val durationSeconds = listOf(300.0, 600.0, 1200.0, 3600.0)
        val powers = listOf(bests.best5min, bests.best10min, bests.best20min, bests.best60min)

        val dataPoints = durationSeconds.zip(powers)
            .filter { (_, power) -> power != null && power > 0 }
            .map { (t, power) -> Pair(t, power!! * t) }

        if (dataPoints.size < 2) return null

        val result = linearRegression(dataPoints) ?: return null
        val (cp, wPrime, rSquared) = result

        if (cp <= 50 || cp >= 600 || wPrime <= 0 || wPrime >= 100_000) {
            log.debug("FTP CP model result implausible: CP={} W'={}", cp, wPrime)
            return null
        }

        return WeightedEstimate(cp.roundToInt().toDouble(), 0.90 * rSquared, "cp_model")
    }

    private fun linearRegression(points: List<Pair<Double, Double>>): CpModelResult? {
        val n = points.size.toDouble()
        val sumX = points.sumOf { it.first }
        val sumY = points.sumOf { it.second }
        val sumXY = points.sumOf { it.first * it.second }
        val sumX2 = points.sumOf { it.first * it.first }

        val denom = n * sumX2 - sumX * sumX
        if (denom == 0.0) return null

        val slope = (n * sumXY - sumX * sumY) / denom
        val intercept = (sumY - slope * sumX) / n

        val meanY = sumY / n
        val ssTot = points.sumOf { (_, y) -> (y - meanY) * (y - meanY) }
        val ssRes = points.sumOf { (x, y) -> (y - slope * x - intercept) * (y - slope * x - intercept) }
        val rSquared = if (ssTot == 0.0) 1.0 else (1.0 - ssRes / ssTot).coerceIn(0.0, 1.0)

        return CpModelResult(slope, intercept, rSquared)
    }

    private fun determineConfidence(estimates: List<WeightedEstimate>, cpResult: WeightedEstimate?): Confidence {
        val strongSignals = estimates.count { it.weight >= 0.85 }
        val rSquared = if (cpResult != null) cpResult.weight / 0.90 else null
        return when {
            strongSignals >= 2 || (rSquared != null && rSquared >= 0.98) -> Confidence.HIGH
            estimates.size >= 2 || (rSquared != null && rSquared >= 0.90) -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
    }
}

