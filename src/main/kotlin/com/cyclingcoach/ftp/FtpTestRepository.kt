package com.cyclingcoach.ftp

import com.cyclingcoach.generated.jooq.tables.FtpTest.Companion.FTP_TEST
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDate

data class FtpTestRow(
    val id: Long,
    val rideId: Long?,
    val date: LocalDate,
    val ftpValue: Double,
    val testType: FtpTestType?,
    val notes: String?,
)

data class NewFtpTest(
    val rideId: Long,
    val date: LocalDate,
    val ftpValue: Double,
    val testType: FtpTestType,
    val notes: String? = null,
    val weightKg: Double? = null,
)

@Repository
class FtpTestRepository(
    private val dsl: DSLContext,
) {
    fun findAll(): List<FtpTestRow> =
        dsl
            .selectFrom(FTP_TEST)
            .orderBy(FTP_TEST.DATE)
            .fetch()
            .map {
                FtpTestRow(
                    id = it.id!!.toLong(),
                    rideId = it.rideId?.toLong(),
                    date = LocalDate.parse(it.date!!),
                    ftpValue = it.ftpValue!!.toDouble(),
                    testType = it.testType?.let { t -> FtpTestType.valueOf(t) },
                    notes = it.notes,
                )
            }

    fun findEffectiveAt(date: LocalDate): Double? =
        dsl
            .select(FTP_TEST.FTP_VALUE)
            .from(FTP_TEST)
            .where(FTP_TEST.DATE.le(date.toString()))
            .and(FTP_TEST.FTP_VALUE.gt(0f))
            .orderBy(FTP_TEST.DATE.desc())
            .limit(1)
            .fetchOne(FTP_TEST.FTP_VALUE)
            ?.toDouble()

    fun findLatestBefore(date: LocalDate): FtpTestRow? =
        dsl
            .selectFrom(FTP_TEST)
            .where(FTP_TEST.DATE.lt(date.toString()))
            .and(FTP_TEST.FTP_VALUE.gt(0f))
            .orderBy(FTP_TEST.DATE.desc())
            .limit(1)
            .fetchOne()
            ?.let {
                FtpTestRow(
                    id = it.id!!.toLong(),
                    rideId = it.rideId?.toLong(),
                    date = LocalDate.parse(it.date!!),
                    ftpValue = it.ftpValue!!.toDouble(),
                    testType = it.testType?.let { t -> FtpTestType.valueOf(t) },
                    notes = it.notes,
                )
            }

    fun existsByRideId(rideId: Long): Boolean = dsl.fetchCount(FTP_TEST, FTP_TEST.RIDE_ID.eq(rideId.toInt())) > 0

    fun save(entry: NewFtpTest) {
        dsl
            .insertInto(FTP_TEST)
            .set(FTP_TEST.RIDE_ID, entry.rideId.toInt())
            .set(FTP_TEST.DATE, entry.date.toString())
            .set(FTP_TEST.FTP_VALUE, entry.ftpValue.toFloat())
            .set(FTP_TEST.TEST_TYPE, entry.testType.name)
            .set(FTP_TEST.NOTES, entry.notes)
            .set(FTP_TEST.WEIGHT_KG, entry.weightKg?.toFloat())
            .onConflictDoNothing()
            .execute()
    }
}
