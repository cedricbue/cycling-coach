package com.cyclingcoach.coaching

import com.cyclingcoach.ftp.FtpService
import com.cyclingcoach.generated.model.DailyRecommendation
import com.cyclingcoach.pmc.TrainingLoadRow
import com.cyclingcoach.pmc.TrainingLoadService
import com.cyclingcoach.ride.RideService
import com.cyclingcoach.settings.SettingsProperties
import com.cyclingcoach.weather.WeatherData
import com.cyclingcoach.weather.WeatherProvider
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.math.roundToInt

@Service
class CoachingService(
    private val trainingLoadService: TrainingLoadService,
    private val ftpService: FtpService,
    private val rideService: RideService,
    private val recommendationRepository: DailyRecommendationRepository,
    private val chatClient: ChatClient,
    private val settingsProperties: SettingsProperties,
    private val ollamaChatProperties: OllamaChatProperties,
    private val weatherProvider: WeatherProvider,
    private val coachingProperties: CoachingProperties,
    private val mapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getDailyRecommendation(
        lat: Double?,
        lon: Double?,
        regenerate: Boolean,
    ): DailyRecommendation {
        val today = LocalDate.now()

        if (!regenerate) {
            val cached = recommendationRepository.findByDate(today)
            if (cached != null) {
                log.debug("Returning cached recommendation for $today")
                return cached.toApiModel()
            }
        }

        val resolvedLat =
            lat ?: coachingProperties.location.lat
                ?: error("No location provided and COACHING_LAT is not configured")
        val resolvedLon =
            lon ?: coachingProperties.location.lon
                ?: error("No location provided and COACHING_LON is not configured")

        val (weather, trainingLoad, ftp, recentRides) = runBlocking {
            val weatherD = async(Dispatchers.IO) { weatherProvider.fetchWeather(resolvedLat, resolvedLon) }
            val loadD = async(Dispatchers.IO) { trainingLoadService.findByDate(today) }
            val ftpD = async(Dispatchers.IO) { ftpService.findEffectiveAt(today) }
            val ridesD = async(Dispatchers.IO) { rideService.listRides(0, 7).content ?: emptyList() }
            FetchResult(weatherD.await(), loadD.await(), ftpD.await(), ridesD.await())
        }

        val prompt =
            buildPrompt(
                weather,
                trainingLoad,
                ftp,
                recentRides.map {
                    RecentRide(
                        date = it.date?.toString() ?: "",
                        name = it.name ?: "Unnamed",
                        tss = it.tss,
                        intensityFactor = it.intensityFactor,
                    )
                },
            )

        val model = ollamaChatProperties.model
        log.info("Calling ollama/$model for daily recommendation")
        val parsed =
            chatClient.prompt(prompt).call().entity(AiOutput::class.java)
                ?: throw IllegalStateException("AI returned empty response")
        val weatherSummary = buildWeatherSummary(weather)
        val providerLabel = "ollama/$model"

        val row =
            DailyRecommendationRow(
                id = 0,
                date = today,
                type = parsed.type,
                content = parsed.content,
                reason = parsed.reason,
                weatherSnapshot = mapper.writeValueAsString(weather),
                aiProvider = providerLabel,
                generatedAt = OffsetDateTime.now(),
            )
        recommendationRepository.upsert(row)

        return row.toApiModel(weatherSummary)
    }

    private data class FetchResult(
        val weather: WeatherData,
        val trainingLoad: TrainingLoadRow?,
        val ftp: Double?,
        val recentRides: List<com.cyclingcoach.generated.model.RideSummary>,
    )

    private data class RecentRide(
        val date: String,
        val name: String,
        val tss: Double?,
        val intensityFactor: Double?,
    )

    private data class AiOutput(
        val type: String,
        val content: String,
        val reason: String,
    )

    private fun buildPrompt(
        weather: WeatherData,
        tl: TrainingLoadRow?,
        ftp: Double?,
        recentRides: List<RecentRide>,
    ): String {
        val ftpVal = ftp ?: 0.0
        val p = settingsProperties.zones.power
        val z1 = (ftpVal * p.z1Max / 100).roundToInt()
        val z2 = (ftpVal * p.z2Max / 100).roundToInt()
        val z3 = (ftpVal * p.z3Max / 100).roundToInt()
        val z4 = (ftpVal * p.z4Max / 100).roundToInt()
        val z5 = (ftpVal * p.z5Max / 100).roundToInt()

        val ctl = tl?.ctl?.roundToInt() ?: 0
        val atl = tl?.atl?.roundToInt() ?: 0
        val tsb = tl?.tsb?.roundToInt() ?: 0
        val atlCtlRatio = if (ctl > 0) String.format("%.2f", (tl?.atl ?: 0.0) / tl!!.ctl) else "N/A"

        // Training monotony: avg TSS / std dev of daily TSS over last 7 rides
        val tssValues = recentRides.mapNotNull { it.tss }
        val monotony =
            if (tssValues.size >= 2) {
                val avg = tssValues.average()
                val stdDev = Math.sqrt(tssValues.map { (it - avg) * (it - avg) }.average())
                if (stdDev > 0) String.format("%.1f", avg / stdDev) else "N/A"
            } else {
                "N/A"
            }

        val ridesText =
            if (recentRides.isEmpty()) {
                "No recent rides recorded."
            } else {
                recentRides.joinToString("\n") { r ->
                    "  - ${r.date}: ${r.name} — TSS ${r.tss?.roundToInt() ?: "?"}, IF ${r.intensityFactor?.let {
                        String.format(
                            "%.2f",
                            it,
                        )
                    } ?: "?"}"
                }
            }

        return """
            You are a professional road cycling coach. Based on the athlete's data and weather, decide today's training.

            ATHLETE STATE
            CTL (fitness): $ctl | ATL (fatigue): $atl | TSB (form): $tsb
            ATL/CTL ratio: $atlCtlRatio (overreaching signal if > 1.3)
            Training monotony (7-day): $monotony (warning if > 2.0)
            FTP: ${ftp?.roundToInt() ?: "unknown"} W
            Power zones (watts): Z1≤${z1}W | Z2≤${z2}W | Z3≤${z3}W | Z4≤${z4}W | Z5≤${z5}W

            LAST 7 RIDES
            $ridesText

            WEATHER (next 6 hours)
            Temperature: ${weather.minTemp.roundToInt()}–${weather.maxTemp.roundToInt()}°C
            Max precipitation probability: ${weather.maxPrecipProb}%
            Max wind gust: ${weather.maxWindGust.roundToInt()} km/h
            Dark during workout window: ${weather.wouldBeDark}

            DECISION RULES (apply in order)
            1. REST if: TSB < -25 OR ATL/CTL ratio > 1.3 OR athlete has no positive TSB in 7+ days
            2. INDOOR if: temperature < 5°C OR rain probability > 70% OR wind gusts > 60 km/h OR dark during workout
            3. OUTDOOR_FUN if: TSB > +15 (well-rested, unstructured play is best) OR (TSB between -5 and +10 AND CTL < 40, base-building phase) OR 3+ consecutive structured rides detected in last 7 days
            4. Otherwise: OUTDOOR structured workout

            FIELD GUIDELINES
            type: one of OUTDOOR, OUTDOOR_FUN, INDOOR, REST — chosen by the rules above.
            reason: 1–2 sentences naming the exact rule and key metric that triggered the decision.
              Example OUTDOOR: "TSB is +8 and conditions are clear — a structured session will build fitness without digging a fatigue hole."
              Example INDOOR: "Wind gusts reach 65 km/h this afternoon, above the 60 km/h outdoor threshold."
              Example REST: "ATL/CTL ratio is 1.42, well above the 1.3 overreaching threshold — another session today would increase injury risk."
              Example OUTDOOR_FUN: "TSB is +18 — you are well rested. Unstructured riding lets you enjoy the form without adding stress."
            content guidelines by type:
            OUTDOOR: Specific zone + duration + interval format. Example: "90 min — 15 min warm-up Z2, 4×10 min at Z4 ($z4–${z5}W) with 5 min easy recovery, 20 min Z2 cooldown. Focus: steady power, not heart rate."
            OUTDOOR_FUN: Duration range only (60–90 min), explicitly remove all targets, permission to disconnect. Example: "60–90 min wherever you feel like going. Leave the power meter in ERG-off, ignore heart rate. Just ride."
            INDOOR: Named Zwift workout, ERG watts, warmup/main set/cooldown. Example: "Zwift — Volcano Flat ERG. 15 min warm-up 60% FTP, 3×15 min SST at ${(ftpVal * 0.88).roundToInt()}W with 5 min recovery, 15 min cooldown. Total: 75 min."
            REST: Sleep target (8–9h), nutrition timing, whether tomorrow resumes load or stays easy.
            """.trimIndent()
    }

    private fun buildWeatherSummary(weather: WeatherData): String {
        val temp = weather.minTemp.roundToInt()
        val rain = weather.maxPrecipProb
        val gust = weather.maxWindGust.roundToInt()
        return "$temp°C, $rain% rain, $gust km/h max gusts"
    }
}

private fun DailyRecommendationRow.toApiModel(weatherSummary: String? = null) =
    DailyRecommendation(
        date = date,
        type = DailyRecommendation.Type.valueOf(type),
        content = content,
        reason = reason,
        weatherSummary = weatherSummary,
        generatedAt = generatedAt,
        aiProvider = aiProvider,
    )
