package com.cyclingcoach.coaching

import com.cyclingcoach.generated.jooq.tables.DailyRecommendation.Companion.DAILY_RECOMMENDATION
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.OffsetDateTime

data class DailyRecommendationRow(
    val id: Long,
    val date: LocalDate,
    val type: String,
    val content: String,
    val reason: String,
    val weatherSnapshot: String?,
    val aiProvider: String,
    val generatedAt: OffsetDateTime,
)

@Repository
class DailyRecommendationRepository(private val dsl: DSLContext) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun findByDate(date: LocalDate): DailyRecommendationRow? =
        dsl.selectFrom(DAILY_RECOMMENDATION)
            .where(DAILY_RECOMMENDATION.DATE.eq(date.toString()))
            .fetchOne()
            ?.let {
                DailyRecommendationRow(
                    id = it.id!!.toLong(),
                    date = LocalDate.parse(it.date!!),
                    type = it.type!!,
                    content = it.content!!,
                    reason = it.reason ?: "",
                    weatherSnapshot = it.weatherSnapshot,
                    aiProvider = it.aiProvider!!,
                    generatedAt = OffsetDateTime.parse(it.generatedAt!!),
                )
            }

    fun upsert(row: DailyRecommendationRow) {
        dsl.insertInto(DAILY_RECOMMENDATION)
            .set(DAILY_RECOMMENDATION.DATE, row.date.toString())
            .set(DAILY_RECOMMENDATION.TYPE, row.type)
            .set(DAILY_RECOMMENDATION.CONTENT, row.content)
            .set(DAILY_RECOMMENDATION.REASON, row.reason)
            .set(DAILY_RECOMMENDATION.WEATHER_SNAPSHOT, row.weatherSnapshot)
            .set(DAILY_RECOMMENDATION.AI_PROVIDER, row.aiProvider)
            .set(DAILY_RECOMMENDATION.GENERATED_AT, row.generatedAt.toString())
            .onConflict(DAILY_RECOMMENDATION.DATE)
            .doUpdate()
            .set(DAILY_RECOMMENDATION.TYPE, row.type)
            .set(DAILY_RECOMMENDATION.CONTENT, row.content)
            .set(DAILY_RECOMMENDATION.REASON, row.reason)
            .set(DAILY_RECOMMENDATION.WEATHER_SNAPSHOT, row.weatherSnapshot)
            .set(DAILY_RECOMMENDATION.AI_PROVIDER, row.aiProvider)
            .set(DAILY_RECOMMENDATION.GENERATED_AT, row.generatedAt.toString())
            .execute()
        log.debug("Upserted recommendation for ${row.date} (type=${row.type})")
    }
}
