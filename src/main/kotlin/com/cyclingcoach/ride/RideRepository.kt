package com.cyclingcoach.ride

import com.cyclingcoach.ftp.RidePowerSample
import com.cyclingcoach.generated.jooq.tables.GarminActivity.Companion.GARMIN_ACTIVITY
import com.cyclingcoach.generated.jooq.tables.Ride.Companion.RIDE
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class RideMetrics(
    val durationSeconds: Double?,
    val avgPower: Double?,
    val variabilityIndex: Double?,
    val bestPower1min: Double?,
    val bestPower5min: Double?,
    val bestPower10min: Double?,
    val bestPower20min: Double?,
    val bestPower60min: Double?,
)

internal data class RideSummaryRow(
    val id: Long?,
    val name: String?,
    val startTime: OffsetDateTime?,
    val manufacturer: String?,
    val date: LocalDate?,
    val distanceKm: Double?,
    val durationSeconds: Double?,
    val elevationGainM: Double?,
    val avgSpeedKmh: Double?,
    val avgPowerW: Double?,
    val normalizedPowerW: Double?,
    val tss: Double?,
    val intensityFactor: Double?,
)

internal data class RideDetailRow(
    val id: Long?,
    val externalId: String?,
    val name: String?,
    val startTime: OffsetDateTime?,
    val manufacturer: String?,
    val distanceKm: Double?,
    val elevationGainM: Double?,
    val elevationDescentM: Double?,
    val durationSeconds: Double?,
    val avgPowerW: Double?,
    val maxPowerW: Double?,
    val avgHrBpm: Double?,
    val maxHrBpm: Double?,
    val avgCadenceRpm: Double?,
    val maxCadenceRpm: Double?,
    val normalizedPowerW: Double?,
    val intensityFactor: Double?,
    val tss: Double?,
    val ftpAtRide: Double?,
    val wattsPerKg: Double?,
    val bestPower5sW: Double?,
    val bestPower30sW: Double?,
    val bestPower1minW: Double?,
    val bestPower5minW: Double?,
    val bestPower10minW: Double?,
    val bestPower20minW: Double?,
    val bestPower60minW: Double?,
    val rpe: Int?,
    val coachSummary: String?,
    val notes: String?,
)

@Repository
class RideRepository(
    private val dsl: DSLContext,
) {
    fun save(input: RideInput): Long {
        dsl
            .insertInto(RIDE)
            .set(RIDE.ACTIVITY_ID, input.activityId.toInt())
            .set(RIDE.EXTERNAL_ID, input.externalId)
            .set(RIDE.DATE, input.date.toString())
            .set(RIDE.NAME, input.name)
            .set(RIDE.START_TIME, input.startTime)
            .set(RIDE.MANUFACTURER, input.manufacturer)
            .set(RIDE.DISTANCE, input.distanceMeters?.toFloat())
            .set(RIDE.ELEVATION_GAIN, input.elevationGain?.toFloat())
            .set(RIDE.ELEVATION_DESCENT, input.elevationDescent?.toFloat())
            .set(RIDE.DURATION, input.durationSeconds?.toFloat())
            .set(RIDE.AVG_POWER, input.avgPower?.toFloat())
            .set(RIDE.MAX_POWER, input.maxPower?.toFloat())
            .set(RIDE.AVG_HR, input.avgHr?.toFloat())
            .set(RIDE.MAX_HR, input.maxHr?.toFloat())
            .set(RIDE.AVG_CADENCE, input.avgCadence?.toFloat())
            .set(RIDE.MAX_CADENCE, input.maxCadence?.toFloat())
            .set(RIDE.AVG_GRADE, input.avgGrade?.toFloat())
            .set(RIDE.MAX_GRADE, input.maxGrade?.toFloat())
            .set(RIDE.NORMALIZED_POWER, input.normalizedPower?.toFloat())
            .set(RIDE.INTENSITY_FACTOR, input.intensityFactor?.toFloat())
            .set(RIDE.TSS, input.tss?.toFloat())
            .set(RIDE.BEST_POWER_5S, input.bestPower5s?.toFloat())
            .set(RIDE.BEST_POWER_30S, input.bestPower30s?.toFloat())
            .set(RIDE.BEST_POWER_1MIN, input.bestPower1min?.toFloat())
            .set(RIDE.BEST_POWER_5MIN, input.bestPower5min?.toFloat())
            .set(RIDE.BEST_POWER_10MIN, input.bestPower10min?.toFloat())
            .set(RIDE.BEST_POWER_20MIN, input.bestPower20min?.toFloat())
            .set(RIDE.BEST_POWER_60MIN, input.bestPower60min?.toFloat())
            .set(RIDE.WATTS_PER_KG, input.wattsPerKg?.toFloat())
            .set(RIDE.FTP, input.ftp?.toFloat())
            .set(RIDE.AVG_SPEED_MPS, input.avgSpeedMps?.toFloat())
            .set(RIDE.MAX_SPEED_MPS, input.maxSpeedMps?.toFloat())
            .set(RIDE.VARIABILITY_INDEX, input.variabilityIndex?.toFloat())
            .set(RIDE.EFFICIENCY_FACTOR, input.efficiencyFactor?.toFloat())
            .onConflict(RIDE.EXTERNAL_ID)
            .doUpdate()
            .set(RIDE.ACTIVITY_ID, input.activityId.toInt())
            .set(RIDE.DATE, input.date.toString())
            .set(RIDE.NAME, input.name)
            .set(RIDE.START_TIME, input.startTime)
            .set(RIDE.MANUFACTURER, input.manufacturer)
            .set(RIDE.DISTANCE, input.distanceMeters?.toFloat())
            .set(RIDE.ELEVATION_GAIN, input.elevationGain?.toFloat())
            .set(RIDE.ELEVATION_DESCENT, input.elevationDescent?.toFloat())
            .set(RIDE.DURATION, input.durationSeconds?.toFloat())
            .set(RIDE.AVG_POWER, input.avgPower?.toFloat())
            .set(RIDE.MAX_POWER, input.maxPower?.toFloat())
            .set(RIDE.AVG_HR, input.avgHr?.toFloat())
            .set(RIDE.MAX_HR, input.maxHr?.toFloat())
            .set(RIDE.AVG_CADENCE, input.avgCadence?.toFloat())
            .set(RIDE.MAX_CADENCE, input.maxCadence?.toFloat())
            .set(RIDE.AVG_GRADE, input.avgGrade?.toFloat())
            .set(RIDE.MAX_GRADE, input.maxGrade?.toFloat())
            .set(RIDE.NORMALIZED_POWER, input.normalizedPower?.toFloat())
            // FTP-dependent fields: keep the existing non-null value when a concurrent compute has no FTP yet
            .set(RIDE.FTP, DSL.coalesce(DSL.`val`(input.ftp?.toFloat()), RIDE.FTP))
            .set(RIDE.INTENSITY_FACTOR, DSL.coalesce(DSL.`val`(input.intensityFactor?.toFloat()), RIDE.INTENSITY_FACTOR))
            .set(RIDE.TSS, DSL.coalesce(DSL.`val`(input.tss?.toFloat()), RIDE.TSS))
            .set(RIDE.WATTS_PER_KG, DSL.coalesce(DSL.`val`(input.wattsPerKg?.toFloat()), RIDE.WATTS_PER_KG))
            .set(RIDE.BEST_POWER_5S, input.bestPower5s?.toFloat())
            .set(RIDE.BEST_POWER_30S, input.bestPower30s?.toFloat())
            .set(RIDE.BEST_POWER_1MIN, input.bestPower1min?.toFloat())
            .set(RIDE.BEST_POWER_5MIN, input.bestPower5min?.toFloat())
            .set(RIDE.BEST_POWER_10MIN, input.bestPower10min?.toFloat())
            .set(RIDE.BEST_POWER_20MIN, input.bestPower20min?.toFloat())
            .set(RIDE.BEST_POWER_60MIN, input.bestPower60min?.toFloat())
            .set(RIDE.AVG_SPEED_MPS, input.avgSpeedMps?.toFloat())
            .set(RIDE.MAX_SPEED_MPS, input.maxSpeedMps?.toFloat())
            .set(RIDE.VARIABILITY_INDEX, input.variabilityIndex?.toFloat())
            .set(RIDE.EFFICIENCY_FACTOR, input.efficiencyFactor?.toFloat())
            .execute()
        return dsl
            .select(RIDE.ID)
            .from(RIDE)
            .where(RIDE.EXTERNAL_ID.eq(input.externalId))
            .fetchOne(RIDE.ID)!!
            .toLong()
    }

    fun findActivityIdsSince(from: LocalDate): List<Long> =
        dsl
            .select(RIDE.ACTIVITY_ID)
            .from(RIDE)
            .where(RIDE.DATE.ge(from.toString()))
            .and(RIDE.ACTIVITY_ID.isNotNull)
            .orderBy(RIDE.DATE)
            .fetch(RIDE.ACTIVITY_ID)
            .filterNotNull()
            .map { it.toLong() }

    fun findActivityIdsByDateRange(
        from: LocalDate,
        to: LocalDate,
    ): List<Long> =
        dsl
            .select(RIDE.ACTIVITY_ID)
            .from(RIDE)
            .where(RIDE.DATE.ge(from.toString()))
            .and(RIDE.DATE.le(to.toString()))
            .and(RIDE.ACTIVITY_ID.isNotNull)
            .fetch(RIDE.ACTIVITY_ID)
            .filterNotNull()
            .map { it.toLong() }

    fun findActivityIdsWithNullTss(): List<Long> =
        dsl
            .select(RIDE.ACTIVITY_ID)
            .from(RIDE)
            .where(RIDE.TSS.isNull)
            .and(RIDE.NORMALIZED_POWER.isNotNull)
            .fetch(RIDE.ACTIVITY_ID)
            .filterNotNull()
            .map { it.toLong() }

    fun findActivityIdsWithoutRide(): List<Long> =
        dsl
            .select(GARMIN_ACTIVITY.ID)
            .from(GARMIN_ACTIVITY)
            .leftJoin(RIDE)
            .on(RIDE.ACTIVITY_ID.eq(GARMIN_ACTIVITY.ID))
            .where(RIDE.ID.isNull)
            .fetch(GARMIN_ACTIVITY.ID)
            .filterNotNull()
            .map { it.toLong() }

    fun findPowerSamplesBefore(
        beforeDate: LocalDate,
        lookbackDays: Int = 90,
    ): List<RidePowerSample> {
        val windowStart = beforeDate.minusDays(lookbackDays.toLong())
        return dsl
            .select(
                RIDE.DURATION,
                RIDE.BEST_POWER_5MIN,
                RIDE.BEST_POWER_10MIN,
                RIDE.BEST_POWER_20MIN,
                RIDE.BEST_POWER_60MIN,
            ).from(RIDE)
            .where(RIDE.DATE.lt(beforeDate.toString()))
            .and(RIDE.DATE.ge(windowStart.toString()))
            .and(RIDE.DURATION.ge(1200f))
            .fetch { record ->
                RidePowerSample(
                    durationSeconds = record[RIDE.DURATION]?.toDouble() ?: 0.0,
                    bestPower5min = record[RIDE.BEST_POWER_5MIN]?.toDouble(),
                    bestPower10min = record[RIDE.BEST_POWER_10MIN]?.toDouble(),
                    bestPower20min = record[RIDE.BEST_POWER_20MIN]?.toDouble(),
                    bestPower60min = record[RIDE.BEST_POWER_60MIN]?.toDouble(),
                )
            }
    }

    fun findMaxHrByRideId(rideId: Long): Int? =
        dsl
            .select(RIDE.MAX_HR)
            .from(RIDE)
            .where(RIDE.ID.eq(rideId.toInt()))
            .fetchOne(RIDE.MAX_HR)
            ?.toInt()

    fun findNameByRideId(rideId: Long): String? =
        dsl
            .select(RIDE.NAME)
            .from(RIDE)
            .where(RIDE.ID.eq(rideId.toInt()))
            .fetchOne(RIDE.NAME)

    fun findMetricsById(rideId: Long): RideMetrics? =
        dsl
            .select(
                RIDE.DURATION,
                RIDE.AVG_POWER,
                RIDE.VARIABILITY_INDEX,
                RIDE.BEST_POWER_1MIN,
                RIDE.BEST_POWER_5MIN,
                RIDE.BEST_POWER_10MIN,
                RIDE.BEST_POWER_20MIN,
                RIDE.BEST_POWER_60MIN,
            ).from(RIDE)
            .where(RIDE.ID.eq(rideId.toInt()))
            .fetchOne { record ->
                RideMetrics(
                    durationSeconds = record[RIDE.DURATION]?.toDouble(),
                    avgPower = record[RIDE.AVG_POWER]?.toDouble(),
                    variabilityIndex = record[RIDE.VARIABILITY_INDEX]?.toDouble(),
                    bestPower1min = record[RIDE.BEST_POWER_1MIN]?.toDouble(),
                    bestPower5min = record[RIDE.BEST_POWER_5MIN]?.toDouble(),
                    bestPower10min = record[RIDE.BEST_POWER_10MIN]?.toDouble(),
                    bestPower20min = record[RIDE.BEST_POWER_20MIN]?.toDouble(),
                    bestPower60min = record[RIDE.BEST_POWER_60MIN]?.toDouble(),
                )
            }

    fun countRides(): Long = dsl.selectCount().from(RIDE).fetchOne(0, Long::class.java) ?: 0L

    internal fun findRidePage(
        page: Int,
        size: Int,
    ): List<RideSummaryRow> =
        dsl
            .select(
                RIDE.ID,
                RIDE.NAME,
                RIDE.START_TIME,
                RIDE.MANUFACTURER,
                RIDE.DATE,
                RIDE.DISTANCE,
                RIDE.DURATION,
                RIDE.ELEVATION_GAIN,
                RIDE.AVG_SPEED_MPS,
                RIDE.AVG_POWER,
                RIDE.NORMALIZED_POWER,
                RIDE.TSS,
                RIDE.INTENSITY_FACTOR,
            ).from(RIDE)
            .orderBy(RIDE.DATE.desc(), RIDE.ID.desc())
            .limit(size)
            .offset(page * size)
            .map { r ->
                RideSummaryRow(
                    id = r[RIDE.ID]?.toLong(),
                    name = r[RIDE.NAME],
                    startTime = r[RIDE.START_TIME]?.let { parseStartTime(it) },
                    manufacturer = r[RIDE.MANUFACTURER],
                    date = r[RIDE.DATE]?.let { LocalDate.parse(it) },
                    distanceKm = r[RIDE.DISTANCE]?.toDouble()?.let { it / 1000.0 },
                    durationSeconds = r[RIDE.DURATION]?.toDouble(),
                    elevationGainM = r[RIDE.ELEVATION_GAIN]?.toDouble(),
                    avgSpeedKmh = r[RIDE.AVG_SPEED_MPS]?.toDouble()?.let { it * 3.6 },
                    avgPowerW = r[RIDE.AVG_POWER]?.toDouble(),
                    normalizedPowerW = r[RIDE.NORMALIZED_POWER]?.toDouble(),
                    tss = r[RIDE.TSS]?.toDouble(),
                    intensityFactor = r[RIDE.INTENSITY_FACTOR]?.toDouble(),
                )
            }

    internal fun findRideDetail(rideId: Long): RideDetailRow? =
        dsl
            .select(
                RIDE.ID,
                GARMIN_ACTIVITY.EXTERNAL_ID,
                RIDE.NAME,
                RIDE.START_TIME,
                RIDE.MANUFACTURER,
                RIDE.DISTANCE,
                RIDE.ELEVATION_GAIN,
                RIDE.ELEVATION_DESCENT,
                RIDE.DURATION,
                RIDE.AVG_POWER,
                RIDE.MAX_POWER,
                RIDE.AVG_HR,
                RIDE.MAX_HR,
                RIDE.AVG_CADENCE,
                RIDE.MAX_CADENCE,
                RIDE.NORMALIZED_POWER,
                RIDE.INTENSITY_FACTOR,
                RIDE.TSS,
                RIDE.FTP,
                RIDE.WATTS_PER_KG,
                RIDE.BEST_POWER_5S,
                RIDE.BEST_POWER_30S,
                RIDE.BEST_POWER_1MIN,
                RIDE.BEST_POWER_5MIN,
                RIDE.BEST_POWER_10MIN,
                RIDE.BEST_POWER_20MIN,
                RIDE.BEST_POWER_60MIN,
                RIDE.RPE,
                RIDE.COACH_SUMMARY,
                RIDE.NOTES,
            ).from(RIDE)
            .innerJoin(GARMIN_ACTIVITY)
            .on(GARMIN_ACTIVITY.ID.eq(RIDE.ACTIVITY_ID))
            .where(RIDE.ID.eq(rideId.toInt()))
            .fetchOne { r ->
                RideDetailRow(
                    id = r[RIDE.ID]?.toLong(),
                    externalId = r[GARMIN_ACTIVITY.EXTERNAL_ID],
                    name = r[RIDE.NAME],
                    startTime = r[RIDE.START_TIME]?.let { parseStartTime(it) },
                    manufacturer = r[RIDE.MANUFACTURER],
                    distanceKm = r[RIDE.DISTANCE]?.toDouble()?.let { it / 1000.0 },
                    elevationGainM = r[RIDE.ELEVATION_GAIN]?.toDouble(),
                    elevationDescentM = r[RIDE.ELEVATION_DESCENT]?.toDouble(),
                    durationSeconds = r[RIDE.DURATION]?.toDouble(),
                    avgPowerW = r[RIDE.AVG_POWER]?.toDouble(),
                    maxPowerW = r[RIDE.MAX_POWER]?.toDouble(),
                    avgHrBpm = r[RIDE.AVG_HR]?.toDouble(),
                    maxHrBpm = r[RIDE.MAX_HR]?.toDouble(),
                    avgCadenceRpm = r[RIDE.AVG_CADENCE]?.toDouble(),
                    maxCadenceRpm = r[RIDE.MAX_CADENCE]?.toDouble(),
                    normalizedPowerW = r[RIDE.NORMALIZED_POWER]?.toDouble(),
                    intensityFactor = r[RIDE.INTENSITY_FACTOR]?.toDouble(),
                    tss = r[RIDE.TSS]?.toDouble(),
                    ftpAtRide = r[RIDE.FTP]?.toDouble(),
                    wattsPerKg = r[RIDE.WATTS_PER_KG]?.toDouble(),
                    bestPower5sW = r[RIDE.BEST_POWER_5S]?.toDouble(),
                    bestPower30sW = r[RIDE.BEST_POWER_30S]?.toDouble(),
                    bestPower1minW = r[RIDE.BEST_POWER_1MIN]?.toDouble(),
                    bestPower5minW = r[RIDE.BEST_POWER_5MIN]?.toDouble(),
                    bestPower10minW = r[RIDE.BEST_POWER_10MIN]?.toDouble(),
                    bestPower20minW = r[RIDE.BEST_POWER_20MIN]?.toDouble(),
                    bestPower60minW = r[RIDE.BEST_POWER_60MIN]?.toDouble(),
                    rpe = r[RIDE.RPE],
                    coachSummary = r[RIDE.COACH_SUMMARY],
                    notes = r[RIDE.NOTES],
                )
            }

    private fun parseStartTime(raw: String): OffsetDateTime? =
        runCatching {
            listOf(
                { OffsetDateTime.parse(raw) },
                {
                    java.time.LocalDateTime
                        .parse(raw, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        .atOffset(ZoneOffset.UTC)
                },
                {
                    java.time.LocalDateTime
                        .parse(raw, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))
                        .atOffset(ZoneOffset.UTC)
                },
                {
                    java.time.LocalDateTime
                        .parse(raw, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))
                        .atOffset(ZoneOffset.UTC)
                },
            ).firstNotNullOf { runCatching { it() }.getOrNull() }
        }.getOrNull()
}
