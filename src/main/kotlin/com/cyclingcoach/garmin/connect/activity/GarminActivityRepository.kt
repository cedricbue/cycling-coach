package com.cyclingcoach.garmin.activity

import com.cyclingcoach.generated.jooq.tables.GarminActivity.Companion.GARMIN_ACTIVITY
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class GarminActivityRepository(private val dsl: DSLContext) {

    fun existsByExternalId(externalId: String): Boolean =
        dsl.fetchExists(dsl.selectFrom(GARMIN_ACTIVITY).where(GARMIN_ACTIVITY.EXTERNAL_ID.eq(externalId)))

    fun findExistingExternalIds(externalIds: Collection<String>): Set<String> =
        dsl.select(GARMIN_ACTIVITY.EXTERNAL_ID)
            .from(GARMIN_ACTIVITY)
            .where(GARMIN_ACTIVITY.EXTERNAL_ID.`in`(externalIds))
            .fetchSet(GARMIN_ACTIVITY.EXTERNAL_ID)
            .filterNotNull()
            .toSet()

    fun upsert(input: GarminActivityInput): Long {
        dsl.insertInto(GARMIN_ACTIVITY)
            .set(GARMIN_ACTIVITY.EXTERNAL_ID, input.externalId)
            .set(GARMIN_ACTIVITY.RAW_TCX, input.rawTcx)
            .set(GARMIN_ACTIVITY.RAW_JSON, input.rawJson)
            .onConflict(GARMIN_ACTIVITY.EXTERNAL_ID)
            .doUpdate()
            .set(GARMIN_ACTIVITY.RAW_TCX, input.rawTcx)
            .set(GARMIN_ACTIVITY.RAW_JSON, input.rawJson)
            .execute()
        return dsl.select(GARMIN_ACTIVITY.ID)
            .from(GARMIN_ACTIVITY)
            .where(GARMIN_ACTIVITY.EXTERNAL_ID.eq(input.externalId))
            .fetchOne(GARMIN_ACTIVITY.ID)!!
            .toLong()
    }

    fun findLatestStartTime(): LocalDate? =
        dsl.select(DSL.max(DSL.field("json_extract({0}, '$.startTimeGMT')", String::class.java, GARMIN_ACTIVITY.RAW_JSON)))
            .from(GARMIN_ACTIVITY)
            .fetchOne()
            ?.value1()
            ?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    fun findExternalIdById(activityId: Long): String? =
        dsl.select(GARMIN_ACTIVITY.EXTERNAL_ID)
            .from(GARMIN_ACTIVITY)
            .where(GARMIN_ACTIVITY.ID.eq(activityId.toInt()))
            .fetchOne(GARMIN_ACTIVITY.EXTERNAL_ID)

    fun findRawTcxById(activityId: Long): String? =
        dsl.select(GARMIN_ACTIVITY.RAW_TCX)
            .from(GARMIN_ACTIVITY)
            .where(GARMIN_ACTIVITY.ID.eq(activityId.toInt()))
            .fetchOne(GARMIN_ACTIVITY.RAW_TCX)

    fun findRawJsonById(activityId: Long): String? =
        dsl.select(GARMIN_ACTIVITY.RAW_JSON)
            .from(GARMIN_ACTIVITY)
            .where(GARMIN_ACTIVITY.ID.eq(activityId.toInt()))
            .fetchOne(GARMIN_ACTIVITY.RAW_JSON)
}
