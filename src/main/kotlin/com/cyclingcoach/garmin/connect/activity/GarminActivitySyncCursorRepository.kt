package com.cyclingcoach.garmin.activity

import com.cyclingcoach.generated.jooq.tables.references.GARMIN_ACTIVITY_SYNC_CURSOR
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class GarminActivitySyncCursorRepository(
    private val dsl: DSLContext,
) {
    fun findSince(): LocalDate? =
        dsl
            .select(GARMIN_ACTIVITY_SYNC_CURSOR.SINCE)
            .from(GARMIN_ACTIVITY_SYNC_CURSOR)
            .where(GARMIN_ACTIVITY_SYNC_CURSOR.ID.eq(1))
            .fetchOne(GARMIN_ACTIVITY_SYNC_CURSOR.SINCE)
            ?.let { LocalDate.parse(it) }

    fun updateSince(date: LocalDate) {
        dsl
            .insertInto(GARMIN_ACTIVITY_SYNC_CURSOR)
            .set(GARMIN_ACTIVITY_SYNC_CURSOR.ID, 1)
            .set(GARMIN_ACTIVITY_SYNC_CURSOR.SINCE, date.toString())
            .onConflict(GARMIN_ACTIVITY_SYNC_CURSOR.ID)
            .doUpdate()
            .set(GARMIN_ACTIVITY_SYNC_CURSOR.SINCE, date.toString())
            .execute()
    }
}
