package com.cyclingcoach.user

import com.cyclingcoach.ftp.FtpTestDetectedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Updates user_profile.current_ftp when a validated FTP test result is detected.
 */
@Component
class FtpUserProfileUpdater(
    private val userProfileRepository: UserProfileRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onFtpTestDetected(event: FtpTestDetectedEvent) {
        userProfileRepository.updateCurrentFtp(event.ftpValue, event.date)
        log.info(
            "User FTP updated to {}W from {} test on {}",
            event.ftpValue.toInt(),
            event.testType,
            event.date,
        )
    }
}
