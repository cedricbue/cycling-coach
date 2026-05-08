package com.cyclingcoach.ftp

import com.cyclingcoach.generated.jooq.tables.FtpTest.Companion.FTP_TEST
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class FtpTestRepository(private val dsl: DSLContext) {

    fun save(date: LocalDate, ftpValue: Double, testType: FtpTestType, notes: String? = null) {
        dsl.insertInto(FTP_TEST)
            .set(FTP_TEST.DATE, date.toString())
            .set(FTP_TEST.FTP_VALUE, ftpValue.toFloat())
            .set(FTP_TEST.TEST_TYPE, testType.name)
            .set(FTP_TEST.NOTES, notes)
            .execute()
    }
}
