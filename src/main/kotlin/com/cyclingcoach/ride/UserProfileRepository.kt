package com.cyclingcoach.ride

import com.cyclingcoach.generated.jooq.tables.UserProfile.Companion.USER_PROFILE
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class UserProfileRepository(private val dsl: DSLContext) {

    fun findCurrentFtp(): Double? =
        dsl.select(USER_PROFILE.CURRENT_FTP)
            .from(USER_PROFILE)
            .where(USER_PROFILE.ID.eq(1))
            .fetchOne(USER_PROFILE.CURRENT_FTP)
            ?.toDouble()

    fun findCurrentWeightKg(): Double? =
        dsl.select(USER_PROFILE.CURRENT_WEIGHT_KG)
            .from(USER_PROFILE)
            .where(USER_PROFILE.ID.eq(1))
            .fetchOne(USER_PROFILE.CURRENT_WEIGHT_KG)
            ?.toDouble()
}
