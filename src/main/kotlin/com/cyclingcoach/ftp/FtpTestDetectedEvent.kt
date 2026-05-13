package com.cyclingcoach.ftp

import java.time.LocalDate

/** Published when an FTP test ride is detected and the calculated FTP has passed validation. */
data class FtpTestDetectedEvent(
    val rideId: Long,
    val date: LocalDate,
    val testType: FtpTestType,
    val ftpValue: Double,
)
