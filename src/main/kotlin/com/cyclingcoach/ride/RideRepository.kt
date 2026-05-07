package com.cyclingcoach.ride

import com.cyclingcoach.generated.jooq.tables.Activity.Companion.ACTIVITY
import com.cyclingcoach.generated.jooq.tables.Ride.Companion.RIDE
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class RideRepository(private val dsl: DSLContext) {

    fun save(input: RideInput): Long {
        dsl.insertInto(RIDE)
            .set(RIDE.ACTIVITY_ID, input.activityId.toInt())
            .set(RIDE.DATE, input.date.toString())
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
            .execute()
        return dsl.select(RIDE.ID)
            .from(RIDE)
            .where(RIDE.ACTIVITY_ID.eq(input.activityId.toInt()))
            .fetchOne(RIDE.ID)!!.toLong()
    }

    fun existsByActivityId(activityId: Long): Boolean =
        dsl.fetchExists(dsl.selectFrom(RIDE).where(RIDE.ACTIVITY_ID.eq(activityId.toInt())))

    fun findActivityIdsWithoutRide(): List<Long> =
        dsl.select(ACTIVITY.ID)
            .from(ACTIVITY)
            .leftJoin(RIDE).on(RIDE.ACTIVITY_ID.eq(ACTIVITY.ID))
            .where(RIDE.ID.isNull)
            .fetch(ACTIVITY.ID)
            .filterNotNull()
            .map { it.toLong() }
}
