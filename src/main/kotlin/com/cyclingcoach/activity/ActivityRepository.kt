package com.cyclingcoach.activity

import com.cyclingcoach.generated.jooq.tables.Activity.Companion.ACTIVITY
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Repository
class ActivityRepository(
    private val dsl: DSLContext,
) {
    private val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun existsByExternalId(externalId: String): Boolean =
        dsl.fetchExists(dsl.selectFrom(ACTIVITY).where(ACTIVITY.EXTERNAL_ID.eq(externalId)))

    fun save(
        externalId: String,
        name: String?,
        startTime: String,
        rawTcx: String,
    ): Long =
        dsl
            .insertInto(ACTIVITY)
            .set(ACTIVITY.EXTERNAL_ID, externalId)
            .set(ACTIVITY.NAME, name)
            .set(ACTIVITY.START_TIME, startTime)
            .set(ACTIVITY.RAW_TCX, rawTcx)
            .returningResult(ACTIVITY.ID)
            .fetchOne()!!
            .value1()!!
            .toLong()

    // TODO this must fetch and sort all activities - will not scale well in the future - discuss possible refactoring
    fun findLastSyncTime(): LocalDateTime? =
        dsl
            .select(ACTIVITY.LAST_SYNC_TIME.max())
            .from(ACTIVITY)
            .fetchOne()
            ?.value1()
            ?.let { LocalDateTime.parse(it, dtf) }
}
