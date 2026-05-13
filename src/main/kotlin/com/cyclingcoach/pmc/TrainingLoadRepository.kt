package com.cyclingcoach.pmc

import com.cyclingcoach.generated.jooq.tables.Ride.Companion.RIDE
import com.cyclingcoach.generated.jooq.tables.TrainingLoad.Companion.TRAINING_LOAD
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDate

data class TrainingLoadRow(
    val date: LocalDate,
    val tss: Double,
    val ctl: Double,
    val atl: Double,
    val tsb: Double,
)

@Repository
class TrainingLoadRepository(
    private val dsl: DSLContext,
) {
    fun findByDate(date: LocalDate): TrainingLoadRow? =
        dsl
            .selectFrom(TRAINING_LOAD)
            .where(TRAINING_LOAD.DATE.eq(date.toString()))
            .fetchOne()
            ?.let {
                TrainingLoadRow(
                    date = LocalDate.parse(it.date!!),
                    tss = it.tss!!.toDouble(),
                    ctl = it.ctl!!.toDouble(),
                    atl = it.atl!!.toDouble(),
                    tsb = it.tsb!!.toDouble(),
                )
            }

    fun upsert(
        date: LocalDate,
        tss: Double,
        ctl: Double,
        atl: Double,
        tsb: Double,
    ) {
        dsl
            .insertInto(TRAINING_LOAD)
            .set(TRAINING_LOAD.DATE, date.toString())
            .set(TRAINING_LOAD.TSS, tss.toFloat())
            .set(TRAINING_LOAD.CTL, ctl.toFloat())
            .set(TRAINING_LOAD.ATL, atl.toFloat())
            .set(TRAINING_LOAD.TSB, tsb.toFloat())
            .onConflict(TRAINING_LOAD.DATE)
            .doUpdate()
            .set(TRAINING_LOAD.TSS, tss.toFloat())
            .set(TRAINING_LOAD.CTL, ctl.toFloat())
            .set(TRAINING_LOAD.ATL, atl.toFloat())
            .set(TRAINING_LOAD.TSB, tsb.toFloat())
            .execute()
    }

    fun findBetween(
        from: LocalDate?,
        to: LocalDate?,
    ): List<TrainingLoadRow> {
        var query = dsl.selectFrom(TRAINING_LOAD).where(DSL.trueCondition())
        if (from != null) query = query.and(TRAINING_LOAD.DATE.ge(from.toString()))
        if (to != null) query = query.and(TRAINING_LOAD.DATE.le(to.toString()))
        return query
            .orderBy(TRAINING_LOAD.DATE)
            .fetch()
            .map {
                TrainingLoadRow(
                    date = LocalDate.parse(it.date!!),
                    tss = it.tss!!.toDouble(),
                    ctl = it.ctl!!.toDouble(),
                    atl = it.atl!!.toDouble(),
                    tsb = it.tsb!!.toDouble(),
                )
            }
    }

    fun findLatestDate(): LocalDate? =
        dsl
            .select(DSL.max(TRAINING_LOAD.DATE))
            .from(TRAINING_LOAD)
            .fetchOne()
            ?.value1()
            ?.let { LocalDate.parse(it) }

    // Cross-table query: pmc owns EWMA recalculation and exclusively needs daily TSS aggregates from ride
    fun findDailyTssSince(startDate: LocalDate): Map<LocalDate, Double> =
        dsl
            .select(RIDE.DATE, DSL.sum(RIDE.TSS))
            .from(RIDE)
            .where(RIDE.DATE.ge(startDate.toString()))
            .and(RIDE.TSS.isNotNull)
            .groupBy(RIDE.DATE)
            .orderBy(RIDE.DATE)
            .fetch()
            .associate { LocalDate.parse(it.value1()!!) to it.value2()!!.toDouble() }
}
