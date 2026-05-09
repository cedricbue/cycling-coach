package com.cyclingcoach.ride

import com.cyclingcoach.generated.jooq.tables.GarminActivity.Companion.GARMIN_ACTIVITY
import com.cyclingcoach.generated.jooq.tables.Ride.Companion.RIDE
import com.cyclingcoach.generated.model.RideDetail
import com.cyclingcoach.generated.model.RideMetrics
import com.cyclingcoach.generated.model.RidePage
import com.cyclingcoach.generated.model.RideSummary
import org.jooq.DSLContext
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class RideReadService(
    private val dsl: DSLContext,
) {
    fun listRides(
        page: Int,
        size: Int,
    ): RidePage {
        val offset = page * size

        val total =
            dsl
                .selectCount()
                .from(RIDE)
                .fetchOne(0, Long::class.java) ?: 0L

        val rows =
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
                    RIDE.AVG_POWER,
                    RIDE.NORMALIZED_POWER,
                    RIDE.TSS,
                    RIDE.INTENSITY_FACTOR,
                ).from(RIDE)
                .orderBy(RIDE.DATE.desc(), RIDE.ID.desc())
                .limit(size)
                .offset(offset)
                .map { r ->
                    RideSummary(
                        id = r[RIDE.ID]?.toLong(),
                        name = r[RIDE.NAME],
                        startTime = r[RIDE.START_TIME]?.let { parseStartTime(it) },
                        manufacturer = r[RIDE.MANUFACTURER],
                        date = r[RIDE.DATE]?.let { LocalDate.parse(it) },
                        distanceKm = r[RIDE.DISTANCE]?.toDouble()?.let { it / 1000.0 },
                        durationSeconds = r[RIDE.DURATION]?.toDouble(),
                        elevationGainM = r[RIDE.ELEVATION_GAIN]?.toDouble(),
                        avgPowerW = r[RIDE.AVG_POWER]?.toDouble(),
                        normalizedPowerW = r[RIDE.NORMALIZED_POWER]?.toDouble(),
                        tss = r[RIDE.TSS]?.toDouble(),
                        intensityFactor = r[RIDE.INTENSITY_FACTOR]?.toDouble(),
                    )
                }

        return RidePage(
            content = rows,
            totalElements = total,
            page = page,
            propertySize = size,
        )
    }

    fun getRide(rideId: Long): RideDetail? =
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
                RideDetail(
                    id = r[RIDE.ID]?.toLong(),
                    externalId = r[GARMIN_ACTIVITY.EXTERNAL_ID],
                    name = r[RIDE.NAME],
                    startTime = r[RIDE.START_TIME]?.let { parseStartTime(it) },
                    manufacturer = r[RIDE.MANUFACTURER],
                    metrics =
                        RideMetrics(
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
                        ),
                )
            }

    private fun parseStartTime(raw: String): OffsetDateTime? =
        runCatching {
            listOf(
                { OffsetDateTime.parse(raw) },
                {
                    java.time.LocalDateTime
                        .parse(
                            raw,
                            java.time.format.DateTimeFormatter
                                .ofPattern("yyyy-MM-dd HH:mm:ss"),
                        ).atOffset(ZoneOffset.UTC)
                },
                {
                    java.time.LocalDateTime
                        .parse(
                            raw,
                            java.time.format.DateTimeFormatter
                                .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
                        ).atOffset(ZoneOffset.UTC)
                },
                {
                    java.time.LocalDateTime
                        .parse(
                            raw,
                            java.time.format.DateTimeFormatter
                                .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"),
                        ).atOffset(ZoneOffset.UTC)
                },
            ).firstNotNullOf { runCatching { it() }.getOrNull() }
        }.getOrNull()
}
