package com.Anton15K.LocalAIDemo.controller

import com.Anton15K.LocalAIDemo.dto.*
import com.Anton15K.LocalAIDemo.repository.ProblemRepository
import com.Anton15K.LocalAIDemo.service.MathDatasetIngestionService
import com.Anton15K.LocalAIDemo.service.MathProblemImport
import com.Anton15K.LocalAIDemo.service.ProblemRetrievalService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/problems")
class ProblemController(
    private val problemRepository: ProblemRepository,
    private val mathDatasetIngestionService: MathDatasetIngestionService,
    private val problemRetrievalService: ProblemRetrievalService,
    private val dtoMapper: DtoMapper
) {
    private val logger = LoggerFactory.getLogger(ProblemController::class.java)

    /**
     * Get all problems with pagination.
     */
    @GetMapping
    fun getAllProblems(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PageResponse<ProblemResponse>> {
        val pageable = PageRequest.of(page, size)
        val problems = problemRepository.findAll(pageable)
        return ResponseEntity.ok(dtoMapper.toPageResponse(problems) { dtoMapper.toProblemResponse(it) })
    }

    /**
     * Get problem by ID.
     */
    @GetMapping("/{id}")
    fun getProblem(@PathVariable id: UUID): ResponseEntity<ProblemResponse> {
        val problem = problemRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(dtoMapper.toProblemResponse(problem))
    }

    /**
     * Get problems by topic.
     */
    @GetMapping("/by-topic/{topic}")
    fun getProblemsByTopic(
        @PathVariable topic: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PageResponse<ProblemResponse>> {
        val pageable = PageRequest.of(page, size)
        val problems = problemRetrievalService.findByTopic(topic, pageable)
        return ResponseEntity.ok(dtoMapper.toPageResponse(problems) { dtoMapper.toProblemResponse(it) })
    }

    /**
     * Get all available topics.
     */
    @GetMapping("/topics")
    fun getTopics(): ResponseEntity<List<String>> {
        val topics = problemRepository.findAllTopics()
        return ResponseEntity.ok(topics)
    }

    /**
     * Import problems from JSON payload.
     */
    @PostMapping("/import")
    fun importProblems(@RequestBody request: ImportProblemsRequest): ResponseEntity<ImportResultResponse> {
        logger.info("Importing ${request.problems.size} problems")
        val imports = request.problems.map { item ->
            MathProblemImport(
                sourceId = item.sourceId,
                statement = item.statement,
                solution = item.solution,
                topic = item.topic,
                subtopic = item.subtopic,
                difficulty = item.difficulty
            )
        }
        val result = mathDatasetIngestionService.importProblems(imports)
        return ResponseEntity.ok(ImportResultResponse(
            imported = result.imported,
            skipped = result.skipped,
            failed = result.failed,
            message = "Import completed: ${result.imported} imported, ${result.skipped} skipped, ${result.failed} failed"
        ))
    }

    /**
     * Import problems from uploaded JSONL file.
     */
    @PostMapping("/import/jsonl")
    fun importFromJsonl(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("topic") topic: String
    ): ResponseEntity<ImportResultResponse> {
        logger.info("Importing problems from JSONL file: ${file.originalFilename}, topic: $topic")
        val result = mathDatasetIngestionService.importFromJsonl(file.inputStream, topic)
        return ResponseEntity.ok(ImportResultResponse(
            imported = result.imported,
            skipped = result.skipped,
            failed = result.failed,
            message = "Import completed: ${result.imported} imported, ${result.skipped} skipped, ${result.failed} failed"
        ))
    }

    /**
     * Create sample problems for testing.
     */
    @PostMapping("/sample")
    fun createSampleProblems(): ResponseEntity<ImportResultResponse> {
        logger.info("Creating sample problems")
        val result = mathDatasetIngestionService.createSampleProblems()
        return ResponseEntity.ok(ImportResultResponse(
            imported = result.imported,
            skipped = result.skipped,
            failed = result.failed,
            message = "Sample problems created: ${result.imported} imported, ${result.skipped} skipped"
        ))
    }
}
