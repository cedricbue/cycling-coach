package com.cyclingcoach.bikefit

import com.cyclingcoach.generated.api.BikeFitApi
import com.cyclingcoach.generated.model.BikeFitAnalysisDetail
import com.cyclingcoach.generated.model.BikeFitAnalysisSummary
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.nio.file.Path
import java.time.OffsetDateTime

@RestController
class BikeFitController(
    private val bikeFitService: BikeFitService,
    private val sseRegistry: SseEmitterRegistry,
) : BikeFitApi {

    override fun createBikeFitAnalysis(
        video: MultipartFile,
        poseModel: String,
        mediapipeComplexity: Int?,
        rtmposeMode: String?,
        rtmposeSchema: String?,
        device: String,
    ): ResponseEntity<BikeFitAnalysisSummary> {
        val row = bikeFitService.startAnalysis(
            video, poseModel, mediapipeComplexity, rtmposeMode, rtmposeSchema, device,
        )
        return ResponseEntity.status(202).body(row.toSummary())
    }

    override fun listBikeFitAnalyses(): ResponseEntity<List<BikeFitAnalysisSummary>> =
        ResponseEntity.ok(bikeFitService.listAnalyses().map { it.toSummary() })

    override fun getBikeFitAnalysis(id: String): ResponseEntity<BikeFitAnalysisDetail> {
        val row = bikeFitService.findByIdWithLandmarks(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(row.toDetail())
    }

    override fun getBikeFitVideo(id: String): ResponseEntity<Resource> {
        val row = bikeFitService.findById(id) ?: return ResponseEntity.notFound().build()
        val file = Path.of(row.videoPath).toFile()
        if (!file.exists()) return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("video/mp4"))
            .body(FileSystemResource(file))
    }

    @PostMapping("/api/bike-fit/analyses/{id}/retry")
    fun retryBikeFitAnalysis(@PathVariable id: String): ResponseEntity<BikeFitAnalysisSummary> {
        val row = bikeFitService.retryAnalysis(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(row.toSummary())
    }

    @GetMapping(
        "/api/bike-fit/analyses/{id}/events",
        produces = [MediaType.TEXT_EVENT_STREAM_VALUE],
    )
    fun streamEvents(@PathVariable id: String): ResponseEntity<SseEmitter> {
        val row = bikeFitService.findById(id) ?: return ResponseEntity.notFound().build()
        if (row.status != "PROCESSING") {
            val emitter = SseEmitter(0L)
            emitter.send("""{"status":"${row.status}"}""")
            emitter.complete()
            return ResponseEntity.ok(emitter)
        }
        return ResponseEntity.ok(sseRegistry.register(id))
    }

    private fun BikeFitRow.toSummary() = BikeFitAnalysisSummary(
        id = id,
        status = BikeFitAnalysisSummary.Status.valueOf(status),
        poseModel = BikeFitAnalysisSummary.PoseModel.valueOf(poseModel.uppercase()),
        poseSchema = poseSchema?.let { BikeFitAnalysisSummary.PoseSchema.valueOf(it.uppercase()) },
        originalFilename = originalFilename,
        fps = fps,
        totalFrames = totalFrames,
        errorMessage = errorMessage,
        createdAt = createdAt,
        completedAt = completedAt,
    )

    private fun BikeFitRow.toDetail() = BikeFitAnalysisDetail(
        id = id,
        status = BikeFitAnalysisDetail.Status.valueOf(status),
        poseModel = BikeFitAnalysisDetail.PoseModel.valueOf(poseModel.uppercase()),
        poseSchema = poseSchema?.let { BikeFitAnalysisDetail.PoseSchema.valueOf(it.uppercase()) },
        originalFilename = originalFilename,
        fps = fps,
        totalFrames = totalFrames,
        errorMessage = errorMessage,
        createdAt = createdAt,
        completedAt = completedAt,
        landmarksJson = landmarksJson,
    )
}
