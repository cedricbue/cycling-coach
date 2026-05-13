package com.cyclingcoach.ftp

import com.cyclingcoach.generated.api.FtpApi
import com.cyclingcoach.generated.model.FtpEntry
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class FtpController(
    private val repository: FtpTestRepository,
) : FtpApi {
    override fun getFtpHistory(): ResponseEntity<List<FtpEntry>> {
        val entries =
            repository.findAll().map { row ->
                FtpEntry(
                    id = row.id,
                    date = row.date,
                    ftpValue = row.ftpValue,
                    testType =
                        when (row.testType) {
                            FtpTestType.ESTIMATED -> FtpEntry.TestType.ESTIMATED
                            else -> FtpEntry.TestType.AUTO_DETECTED
                        },
                    notes = row.notes,
                    rideId = row.rideId,
                )
            }
        return ResponseEntity.ok(entries)
    }
}
