package com.cyclingcoach.settings

import com.cyclingcoach.ftp.FtpService
import com.cyclingcoach.generated.model.AppSettings
import com.cyclingcoach.generated.model.HrZoneSettings
import com.cyclingcoach.generated.model.PowerZoneSettings
import com.cyclingcoach.user.UserProfileService
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class SettingsService(
    private val props: SettingsProperties,
    private val userProfileService: UserProfileService,
    private val ftpService: FtpService,
    private val ollamaChatProperties: OllamaChatProperties,
) {
    fun getAppSettings(): AppSettings {
        val p = props.zones.power
        val h = props.zones.hr

        return AppSettings(
            aiProvider = AppSettings.AiProvider.OLLAMA,
            aiModel = ollamaChatProperties.model,
            currentFtp = ftpService.findEffectiveAt(LocalDate.now()),
            weightKg = userProfileService.findLatestWeightKg(),
            maxHrBpm = userProfileService.findMaxHr(),
            powerZones =
                PowerZoneSettings(
                    z1Max = p.z1Max,
                    z2Max = p.z2Max,
                    z3Max = p.z3Max,
                    z4Max = p.z4Max,
                    z5Max = p.z5Max,
                ),
            hrZones =
                HrZoneSettings(
                    z1Max = h.z1Max,
                    z2Max = h.z2Max,
                    z3Max = h.z3Max,
                    z4Max = h.z4Max,
                ),
        )
    }
}
