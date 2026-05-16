package com.cyclingcoach.bikefit

import com.cyclingcoach.config.VIRTUAL_THREAD_EXECUTOR
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.*

@Service
class BikeFitService(
    private val properties: BikeFitProperties,
    private val repository: BikeFitRepository,
    private val landmarksApiClient: LandmarksApiClient,
    private val sseRegistry: SseEmitterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun startAnalysis(
        file: MultipartFile,
        poseModel: String,
        mediapipeComplexity: Int?,
        rtmposeMode: String?,
        rtmposeSchema: String?,
        device: String,
    ): BikeFitRow {
        val id = UUID.randomUUID().toString()
        val ext = file.originalFilename?.substringAfterLast('.', "mp4") ?: "mp4"
        val dir = Path.of(properties.dataDir, id)
        Files.createDirectories(dir)
        val videoPath = dir.resolve("video.$ext")
        Files.write(videoPath, file.bytes)

        val row = BikeFitRow(
            id = id,
            status = "PROCESSING",
            videoPath = videoPath.toString(),
            originalFilename = file.originalFilename ?: "video.$ext",
            poseModel = poseModel,
            poseSchema = null,
            fps = null,
            totalFrames = null,
            landmarksJson = null,
            errorMessage = null,
            createdAt = OffsetDateTime.now(),
            completedAt = null,
        )
        repository.insert(row)

        processAsync(id, videoPath, poseModel, mediapipeComplexity, rtmposeMode, rtmposeSchema, device)
        return row
    }

    @Async(VIRTUAL_THREAD_EXECUTOR)
    fun processAsync(
        id: String,
        videoPath: Path,
        poseModel: String,
        mediapipeComplexity: Int?,
        rtmposeMode: String?,
        rtmposeSchema: String?,
        device: String,
    ) {
        log.info("Starting landmarks analysis for $id (model=$poseModel)")
        try {
            val result = landmarksApiClient.analyze(
                videoPath, poseModel, mediapipeComplexity, rtmposeMode, rtmposeSchema, device,
            )
            val landmarksPath = videoPath.parent.resolve("landmarks.json")
            Files.writeString(landmarksPath, result.rawJson)
            repository.updateDone(id, result)
            sseRegistry.completeOk(id, """{"status":"DONE"}""")
            log.info("Analysis $id completed: ${result.totalFrames} frames at ${result.fps} fps")
        } catch (e: Exception) {
            log.error("Analysis $id failed: ${e.message}", e)
            repository.updateFailed(id, e.message)
            sseRegistry.completeError(id, """{"status":"FAILED"}""")
        }
    }

    fun retryAnalysis(id: String): BikeFitRow? {
        val row = repository.findById(id) ?: return null
        if (row.status != "FAILED") return row
        repository.resetToProcessing(id)
        val videoPath = Path.of(row.videoPath)
        processAsync(id, videoPath, row.poseModel, null, null, null, "auto")
        return repository.findById(id)
    }

    fun listAnalyses(): List<BikeFitRow> = repository.findAll()

    fun findById(id: String): BikeFitRow? = repository.findById(id)

    fun findByIdWithLandmarks(id: String): BikeFitRow? {
        val row = repository.findById(id) ?: return null
        if (row.status != "DONE") return row
        val landmarksPath = Path.of(row.videoPath).parent.resolve("landmarks.json")
        val json = runCatching { Files.readString(landmarksPath) }.getOrNull()
        return row.copy(landmarksJson = json)
    }
}
