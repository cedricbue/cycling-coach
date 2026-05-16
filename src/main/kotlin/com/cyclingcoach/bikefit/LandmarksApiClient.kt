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

data class AnalysisResult(
    val rawJson: String,
    val poseModel: String,
    val schema: String?,
    val fps: Double,
    val totalFrames: Int,
)

@Component
class LandmarksApiClient(
    private val properties: BikeFitProperties,
    private val objectMapper: ObjectMapper,
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(properties.landmarksReadTimeoutMinutes, TimeUnit.MINUTES)
        .writeTimeout(properties.landmarksWriteTimeoutMinutes, TimeUnit.MINUTES)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    fun analyze(
        videoPath: Path,
        poseModel: String,
        mediapipeComplexity: Int?,
        rtmposeMode: String?,
        rtmposeSchema: String?,
        device: String,
    ): AnalysisResult {
        val file = videoPath.toFile()

        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("video", file.name, file.asRequestBody("video/mp4".toMediaType()))
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

        // Parse only the metadata fields we need for the DB; the raw JSON is stored to disk as-is.
        // Generated model classes (com.cyclingcoach.generated.landmarks) serve as spec reference.
        val node = objectMapper.readTree(responseBody)
        return AnalysisResult(
            rawJson = responseBody,
            poseModel = node["pose_model"]?.asText() ?: poseModel,
            schema = node["schema"]?.asText(),
            fps = node["fps"]?.asDouble() ?: 0.0,
            totalFrames = node["total_frames"]?.asInt() ?: 0,
        )
    }
}

class LandmarksApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
