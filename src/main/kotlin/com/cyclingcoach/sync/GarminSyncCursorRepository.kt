package com.cyclingcoach.sync

import com.cyclingcoach.generated.jooq.tables.references.GARMIN_SYNC_CURSOR
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class GarminSyncCursorRepository(private val dsl: DSLContext) {

    fun findSince(): LocalDate? =
        dsl.select(GARMIN_SYNC_CURSOR.SINCE)
            .from(GARMIN_SYNC_CURSOR)
            .where(GARMIN_SYNC_CURSOR.ID.eq(1))
            .fetchOne(GARMIN_SYNC_CURSOR.SINCE)
            ?.let { LocalDate.parse(it) }

    fun updateSince(date: LocalDate) {
        dsl.insertInto(GARMIN_SYNC_CURSOR)
            .set(GARMIN_SYNC_CURSOR.ID, 1)
            .set(GARMIN_SYNC_CURSOR.SINCE, date.toString())
            .onConflict(GARMIN_SYNC_CURSOR.ID)
            .doUpdate()
            .set(GARMIN_SYNC_CURSOR.SINCE, date.toString())
            .execute()
    }
}
