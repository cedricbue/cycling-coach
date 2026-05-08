package com.cyclingcoach.activity

import com.cyclingcoach.generated.jooq.tables.Activity.Companion.ACTIVITY
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
@Repository
class ActivityRepository(
    private val dsl: DSLContext,
) {
    fun existsByExternalId(externalId: String): Boolean =
        dsl.fetchExists(dsl.selectFrom(ACTIVITY).where(ACTIVITY.EXTERNAL_ID.eq(externalId)))

    fun findExistingExternalIds(externalIds: Collection<String>): Set<String> =
        dsl
            .select(ACTIVITY.EXTERNAL_ID)
            .from(ACTIVITY)
            .where(ACTIVITY.EXTERNAL_ID.`in`(externalIds))
            .fetchSet(ACTIVITY.EXTERNAL_ID)
            .filterNotNull()
            .toSet()

    fun saveIfNew(input: ActivityInput): Long? {
        val affected =
            dsl
                .insertInto(ACTIVITY)
                .set(ACTIVITY.EXTERNAL_ID, input.externalId)
                .set(ACTIVITY.NAME, input.name)
                .set(ACTIVITY.START_TIME, input.startTimeGmt)
                .set(ACTIVITY.RAW_TCX, input.rawTcx)
                .onConflictDoNothing()
                .execute()
        if (affected == 0) return null
        return dsl
            .select(ACTIVITY.ID)
            .from(ACTIVITY)
            .where(ACTIVITY.EXTERNAL_ID.eq(input.externalId))
            .fetchOne(ACTIVITY.ID)
            ?.toLong()
    }

    fun findLatestStartTime(): String? =
        dsl
            .select(ACTIVITY.START_TIME.max())
            .from(ACTIVITY)
            .fetchOne()
            ?.value1()

    fun findRawTcxById(activityId: Long): String? =
        dsl
            .select(ACTIVITY.RAW_TCX)
            .from(ACTIVITY)
            .where(ACTIVITY.ID.eq(activityId.toInt()))
            .fetchOne(ACTIVITY.RAW_TCX)

    fun findNameById(activityId: Long): String? =
        dsl
            .select(ACTIVITY.NAME)
            .from(ACTIVITY)
            .where(ACTIVITY.ID.eq(activityId.toInt()))
            .fetchOne(ACTIVITY.NAME)
}
