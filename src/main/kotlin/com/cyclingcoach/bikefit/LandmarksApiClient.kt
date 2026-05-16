package com.cyclingcoach.bikefit

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.util.concurrent.TimeUnit

data class LandmarksReport(
    val poseModel: String,
    val poseSchema: String,
    val fps: Double,
    val totalFrames: Int,
    val frames: List<FrameLandmarks>,
)

data class FrameLandmarks(
    val frameIndex: Int,
    val ts: Double,
    val landmarks: List<Landmark>,
)

data class Landmark(
    val x: Double,
    val y: Double,
    val z: Double? = null,
    val visibility: Double? = null,
)

@Component
class LandmarksApiClient(
    private val properties: BikeFitProperties,
    private val objectMapper: ObjectMapper,
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    fun analyze(
        videoPath: Path,
        poseModel: String,
        mediapipeComplexity: Int?,
        rtmposeMode: String?,
        rtmposeSchema: String?,
        device: String,
    ): LandmarksReport {
        val file = videoPath.toFile()
        val mediaType = "video/mp4".toMediaType()

        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("video", file.name, file.asRequestBody(mediaType))
            .addFormDataPart("pose_model", poseModel)
            .addFormDataPart("device", device)

        mediapipeComplexity?.let { bodyBuilder.addFormDataPart("mediapipe_complexity", it.toString()) }
        rtmposeMode?.let { bodyBuilder.addFormDataPart("rtmpose_mode", it) }
        rtmposeSchema?.let { bodyBuilder.addFormDataPart("rtmpose_schema", it) }

        val request = Request.Builder()
            .url("${properties.landmarksApiUrl}/analyze")
            .post(bodyBuilder.build())
            .build()

        val responseBody = client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw LandmarksApiException("Landmarks API returned ${response.code}: ${body.take(300)}")
            }
            body
        }

        return objectMapper.readValue(responseBody, LandmarksReport::class.java)
    }
}

class LandmarksApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
