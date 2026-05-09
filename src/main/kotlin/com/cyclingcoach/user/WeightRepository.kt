package com.cyclingcoach.user

import com.cyclingcoach.generated.jooq.tables.references.USER_WEIGHT
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class WeightRepository(private val dsl: DSLContext) {

    fun upsert(date: LocalDate, weightKg: Double) {
        dsl.insertInto(USER_WEIGHT)
            .set(USER_WEIGHT.DATE, date.toString())
            .set(USER_WEIGHT.WEIGHT_KG, weightKg.toFloat())
            .onConflict(USER_WEIGHT.DATE)
            .doUpdate()
            .set(USER_WEIGHT.WEIGHT_KG, weightKg.toFloat())
            .execute()
    }

    fun findLatestWeight(): Double? =
        dsl.select(USER_WEIGHT.WEIGHT_KG)
            .from(USER_WEIGHT)
            .orderBy(USER_WEIGHT.DATE.desc())
            .limit(1)
            .fetchOne(USER_WEIGHT.WEIGHT_KG)
            ?.toDouble()

    fun findWeightAtOrBefore(date: LocalDate): Double? =
        dsl.select(USER_WEIGHT.WEIGHT_KG)
            .from(USER_WEIGHT)
            .where(USER_WEIGHT.DATE.lessOrEqual(date.toString()))
            .orderBy(USER_WEIGHT.DATE.desc())
            .limit(1)
            .fetchOne(USER_WEIGHT.WEIGHT_KG)
            ?.toDouble()
}
