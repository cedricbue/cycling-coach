package com.cyclingcoach.user

import com.cyclingcoach.generated.jooq.tables.UserProfile.Companion.USER_PROFILE
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class UserProfileRepository(
    private val dsl: DSLContext,
) {
    fun findCurrentFtp(): Double? =
        dsl
            .select(USER_PROFILE.CURRENT_FTP)
            .from(USER_PROFILE)
            .where(USER_PROFILE.ID.eq(1))
            .fetchOne(USER_PROFILE.CURRENT_FTP)
            ?.toDouble()

    fun updateCurrentFtp(
        ftp: Double,
        updatedAt: LocalDate,
    ) {
        dsl
            .update(USER_PROFILE)
            .set(USER_PROFILE.CURRENT_FTP, ftp.toFloat())
            .set(USER_PROFILE.UPDATED_AT, updatedAt.toString())
            .where(USER_PROFILE.ID.eq(1))
            .execute()
    }
}

