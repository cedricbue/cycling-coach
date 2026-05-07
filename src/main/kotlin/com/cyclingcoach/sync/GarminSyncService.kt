package com.cyclingcoach.sync

import com.cyclingcoach.activity.ActivityService
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.time.LocalDateTime

@Service
class GarminSyncService(
    private val garminApiRestClient: RestClient,
    private val garminAuthClient: GarminAuthClient,
    private val garminSessionRepository: GarminSessionRepository,
    private val activityService: ActivityService,
    private val garminProperties: GarminProperties,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun authenticate(
        username: String,
        password: String,
    ) {
        val session = garminAuthClient.authenticate(username, password)
        garminSessionRepository.save(session)
        log.info("Garmin authentication successful — DI tokens saved")
    }

    fun hasValidSession(): Boolean = garminSessionRepository.hasValidSession()

    fun syncActivities() {
        var session =
            resolveSession() ?: run {
                log.warn("No valid Garmin session available — sync aborted")
                return
            }
        log.info("Starting Garmin activity sync")

        val since = activityService.findLastSyncTime()

        val activities =
            withAutoReauth(session) { s ->
                fetchActivities(s.accessToken, since)
            }.let { (result, refreshedSession) ->
                if (refreshedSession != null) session = refreshedSession
                result
            }
        log.info("Fetched {} activities from Garmin Connect", activities.size)

        var newCount = 0
        for (activity in activities) {
            val externalId = activity.activityId.toString()
            if (activityService.existsByExternalId(externalId)) continue

            val tcx =
                withAutoReauth(session) { s ->
                    downloadTcx(s.accessToken, activity.activityId)
                }.let { (result, refreshedSession) ->
                    if (refreshedSession != null) session = refreshedSession
                    result
                }

            activityService.storeIfNew(
                externalId = externalId,
                name = activity.activityName ?: "Untitled",
                startTimeGmt = activity.startTimeGmt ?: "",
                rawTcx = tcx,
            )
            newCount++
        }
        log.info("Sync complete — {} new activities stored", newCount)
    }

    /**
     * Executes [action]. On 401/403, re-authenticates and retries once.
     * Returns (result, newSession). newSession is null if no re-auth occurred.
     */
    private fun <T> withAutoReauth(
        session: GarminSession,
        action: (GarminSession) -> T,
    ): Pair<T, GarminSession?> =
        try {
            action(session) to null
        } catch (e: RestClientResponseException) {
            if (!e.statusCode.isGarminAuthError()) throw e

            log.warn("Garmin returned {} — re-authenticating and retrying", e.statusCode.value())
            val freshSession =
                try {
                    reauthenticate()
                } catch (authEx: Exception) {
                    log.error("Re-authentication failed: {} — aborting sync", authEx.message)
                    throw authEx
                }
            try {
                action(freshSession) to freshSession
            } catch (retryEx: RestClientResponseException) {
                log.error("Garmin still returned {} after re-authentication — aborting", retryEx.statusCode.value())
                throw retryEx
            }
        }

    private fun reauthenticate(): GarminSession {
        val freshSession = garminAuthClient.authenticate(garminProperties.email, garminProperties.password)
        garminSessionRepository.save(freshSession)
        log.info("Full Garmin re-authentication successful")
        return freshSession
    }

    private fun resolveSession(): GarminSession? {
        val session = garminSessionRepository.loadLatest() ?: return null
        if (!session.isExpired()) return session

        log.info("Garmin access token expired — attempting token refresh")
        return if (!session.isRefreshTokenExpired()) {
            try {
                val refreshed = garminAuthClient.refreshToken(session)
                garminSessionRepository.save(refreshed)
                log.info("DI token refreshed successfully")
                refreshed
            } catch (e: Exception) {
                log.warn("Token refresh failed ({}), falling back to full re-auth", e.message)
                tryReauthenticate()
            }
        } else {
            log.info("Refresh token also expired — doing full re-auth")
            tryReauthenticate()
        }
    }

    private fun tryReauthenticate(): GarminSession? =
        try {
            reauthenticate()
        } catch (e: Exception) {
            log.error("Re-authentication failed: {}", e.message)
            null
        }

    private fun fetchActivities(
        accessToken: String,
        since: LocalDateTime?,
    ): List<GarminActivity> {
        val sinceDate =
            since?.toLocalDate()?.toString()
                ?: LocalDateTime.now()
                    .minusDays(garminProperties.sync.initialFetchDays.toLong())
                    .toLocalDate()
                    .toString()

        val raw =
            garminApiRestClient
                .get()
                .uri(
                    "/activitylist-service/activities/search/activities?start=0&limit=100&startDate={since}",
                    sinceDate,
                )
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .retrieve()
                .body(String::class.java) ?: ""

        if (raw.isBlank()) return emptyList()

        val root = objectMapper.readTree(raw)
        val arrayNode: JsonNode =
            when {
                root.isArray -> root
                root.has("activityList") -> root.get("activityList")
                else -> {
                    log.warn("Unexpected activity list response shape (first 200): {}", raw.take(200))
                    return emptyList()
                }
            }
        return objectMapper.readerForListOf(GarminActivity::class.java).readValue(arrayNode)
    }

    private fun downloadTcx(
        accessToken: String,
        activityId: Long,
    ): String =
        garminApiRestClient
            .get()
            .uri("/download-service/export/tcx/activity/{id}", activityId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .retrieve()
            .body(String::class.java) ?: ""

    private companion object {
        val REAUTH_STATUS_CODES = setOf(401, 403)

        fun HttpStatusCode.isGarminAuthError() = value() in REAUTH_STATUS_CODES
    }
}
