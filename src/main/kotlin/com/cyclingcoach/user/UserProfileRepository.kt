package com.cyclingcoach.user

import com.cyclingcoach.generated.jooq.tables.UserProfile.Companion.USER_PROFILE
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class UserProfileRepository(
    private val dsl: DSLContext,
) {
    fun findMaxHr(): Int? =
        dsl
            .select(USER_PROFILE.MAX_HR)
            .from(USER_PROFILE)
            .where(USER_PROFILE.ID.eq(1))
            .fetchOne(USER_PROFILE.MAX_HR)

    fun updateMaxHrIfHigher(newMaxHr: Int) {
        dsl
            .update(USER_PROFILE)
            .set(USER_PROFILE.MAX_HR, newMaxHr)
            .where(USER_PROFILE.ID.eq(1))
            .and(
                USER_PROFILE.MAX_HR.isNull.or(USER_PROFILE.MAX_HR.lt(newMaxHr)),
            )
            .execute()
    }
}
