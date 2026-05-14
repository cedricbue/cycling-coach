package com.cyclingcoach.ftp

import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class FtpService(
    private val ftpTestRepository: FtpTestRepository,
) {
    fun findEffectiveAt(date: LocalDate): Double? = ftpTestRepository.findEffectiveAt(date)

    fun findLatestTestDateBefore(date: LocalDate): LocalDate? = ftpTestRepository.findLatestBefore(date)?.date
}
