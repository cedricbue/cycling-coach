package com.cyclingcoach.client.garmin.internal

import com.cyclingcoach.client.garmin.GarminApiException
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration

internal class GarminHttpClient(
    connectTimeout: Duration = Duration.ofSeconds(10),
    readTimeout: Duration = Duration.ofSeconds(30),
    writeTimeout: Duration = Duration.ofSeconds(15),
) {
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(connectTimeout)
            .readTimeout(readTimeout)
            .writeTimeout(writeTimeout)
            .followRedirects(true)
            .build()

    fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val request = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> request.header(k, v) }
        return execute(request.build())
    }

    fun postForm(
        url: String,
        headers: Map<String, String> = emptyMap(),
        fields: Map<String, String>,
    ): String {
        val body = FormBody.Builder()
        fields.forEach { (k, v) -> body.add(k, v) }
        val request = Request.Builder().url(url).post(body.build())
        headers.forEach { (k, v) -> request.header(k, v) }
        return execute(request.build())
    }

    fun postJson(
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String,
    ): String {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(mediaType))
        headers.forEach { (k, v) -> request.header(k, v) }
        return execute(request.build())
    }

    private fun execute(request: Request): String =
        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    throw GarminApiException(
                        response.code,
                        "HTTP ${response.code} from ${request.url}: ${responseBody.take(200)}",
                    )
                }
                responseBody
            }
        } catch (e: GarminApiException) {
            throw e
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw GarminApiException(0, "Request interrupted: ${request.url}", e)
        } catch (e: Exception) {
            throw GarminApiException(0, "Request failed for ${request.url}: ${e.message}", e)
        }
}
