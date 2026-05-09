package com.cyclingcoach.garmin.connect

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching

/**
 * Shared WireMock stub setup for Garmin Connect authentication.
 * Used by both connect-level tests (AbstractGarminConnectTest)
 * and full-application integration tests (AbstractApplicationIntegrationTest).
 */
object GarminConnectWireMockHelper {

    fun stubAuthFlow(wireMock: WireMockServer) {
        wireMock.stubFor(
            post(urlPathMatching("/mobile/api/login"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"responseStatus":{"type":"SUCCESSFUL"},"serviceTicketId":"ST-TEST-TICKET"}""",
                        ),
                ),
        )
        wireMock.stubFor(
            post(urlPathMatching("/di-oauth2-service/oauth/token"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"access_token":"test-access-token","refresh_token":"test-refresh-token","expires_in":3600,"refresh_token_expires_in":7776000}""",
                        ),
                ),
        )
    }
}
