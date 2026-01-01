package com.Anton15K.LocalAIDemo.controller

import com.Anton15K.LocalAIDemo.dto.*
import com.Anton15K.LocalAIDemo.service.LectureProcessingService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/lectures")
class LectureController(
    private val lectureProcessingService: LectureProcessingService,
    private val dtoMapper: DtoMapper
) {
    private val logger = LoggerFactory.getLogger(LectureController::class.java)

    /**
     * Create a new lecture from transcript text.
     */
    @PostMapping
    fun createLecture(@Valid @RequestBody request: CreateLectureRequest): ResponseEntity<LectureResponse> {
        logger.info("Creating lecture: ${request.title}")
        val lecture = lectureProcessingService.createLecture(
            title = request.title,
            transcript = request.transcript,
            uploadedBy = request.uploadedBy
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(dtoMapper.toLectureResponse(lecture))
    }

    /**
     * Create a new lecture from a video file.
     */
    @PostMapping("/upload-video", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadVideo(
        @RequestParam("title") title: String,
        @RequestParam("video") video: MultipartFile,
        @RequestParam(value = "uploadedBy", required = false) uploadedBy: String?
    ): ResponseEntity<LectureResponse> {
        logger.info("Uploading video for lecture: $title")
        if (video.isEmpty) {
            return ResponseEntity.badRequest().build()
        }
        val videoBytes = video.bytes
        val lecture = lectureProcessingService.createLectureFromVideo(
            title = title,
            videoBytes = videoBytes,
            uploadedBy = uploadedBy
        )
        
        // Background processing
        Thread {
            try {
                lectureProcessingService.transcribeVideoForLecture(lecture.id!!, videoBytes)
                lectureProcessingService.processLecture(lecture.id!!)
            } catch (e: Exception) {
                // Handled in service
            }
        }.start()
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(dtoMapper.toLectureResponse(lecture))
    }

    /**
     * Process a lecture (extract themes).
     */
    @PostMapping("/{id}/process")
    fun processLecture(
        @PathVariable id: UUID,
        @RequestParam(required = false) tuningLevel: Int?,
        @RequestParam(required = false) lectureMinutes: Int?
    ): ResponseEntity<LectureResponse> {
        logger.info("Processing lecture: $id")
        val lecture = lectureProcessingService.processLecture(
            lectureId = id,
            tuningLevel = tuningLevel,
            lectureMinutes = lectureMinutes
        )
        return ResponseEntity.ok(dtoMapper.toLectureResponse(lecture))
    }

    /**
     * Get lecture by ID.
     */
    @GetMapping("/{id}")
    fun getLecture(@PathVariable id: UUID): ResponseEntity<LectureDetailResponse> {
        val lecture = lectureProcessingService.getLecture(id)
            ?: return ResponseEntity.notFound().build()
        val themes = lectureProcessingService.getThemes(id)
        return ResponseEntity.ok(dtoMapper.toLectureDetailResponse(lecture, themes))
    }

    /**
     * Get all lectures with pagination.
     */
    @GetMapping
    fun getAllLectures(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PageResponse<LectureResponse>> {
        val pageable = PageRequest.of(page, size)
        val lectures = lectureProcessingService.getAllLectures(pageable)
        return ResponseEntity.ok(dtoMapper.toPageResponse(lectures) { dtoMapper.toLectureResponse(it) })
    }

    /**
     * Get themes for a lecture.
     */
    @GetMapping("/{id}/themes")
    fun getLectureThemes(@PathVariable id: UUID): ResponseEntity<List<ThemeResponse>> {
        val themes = lectureProcessingService.getThemes(id)
        return ResponseEntity.ok(themes.map { dtoMapper.toThemeResponse(it) })
    }

    /**
     * Get recommended problems for a lecture.
     */
    @GetMapping("/{id}/problems")
    fun getLectureProblems(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "20") topK: Int,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PageResponse<ProblemSearchResultResponse>> {
        val pageable = PageRequest.of(page, size)
        val results = lectureProcessingService.getRecommendedProblems(id, topK, pageable)
        return ResponseEntity.ok(dtoMapper.toPageResponse(results) { dtoMapper.toProblemSearchResultResponse(it) })
    }

    /**
     * Delete a lecture.
     */
    @DeleteMapping("/{id}")
    fun deleteLecture(@PathVariable id: UUID): ResponseEntity<Void> {
        logger.info("Deleting lecture: $id")
        lectureProcessingService.deleteLecture(id)
        return ResponseEntity.noContent().build()
    }
}
