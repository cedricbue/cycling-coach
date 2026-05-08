package com.cyclingcoach.ftp

import java.time.LocalDate

/**
 * Published when an FTP test ride is detected and the calculated FTP has passed validation.
 * A listener will update user_profile.current_ftp in response.
 */
data class FtpTestDetectedEvent(
    val rideId: Long,
    val date: LocalDate,
    val testType: FtpTestType,
    val ftpValue: Double,
)
