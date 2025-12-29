package com.Anton15K.LocalAIDemo.service

import com.Anton15K.LocalAIDemo.domain.*
import com.Anton15K.LocalAIDemo.repository.LectureChunkRepository
import com.Anton15K.LocalAIDemo.repository.LectureRepository
import com.Anton15K.LocalAIDemo.repository.ThemeRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Main service for processing lectures: chunking, theme extraction, and problem retrieval.
 */
@Service
class LectureProcessingService(
    private val lectureRepository: LectureRepository,
    private val lectureChunkRepository: LectureChunkRepository,
    private val themeRepository: ThemeRepository,
    private val textChunkingService: TextChunkingService,
    private val themeExtractionService: ThemeExtractionService,
    private val problemRetrievalService: ProblemRetrievalService
) {
    private val logger = LoggerFactory.getLogger(LectureProcessingService::class.java)

    /**
     * Create a new lecture from transcript text.
     */
    @Transactional
    fun createLecture(title: String, transcript: String, uploadedBy: String? = null): Lecture {
        val lecture = Lecture(
            title = title,
            transcript = transcript,
            uploadedBy = uploadedBy,
            status = LectureStatus.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        return lectureRepository.save(lecture)
    }

    /**
     * Process a lecture: chunk transcript, extract themes, and prepare for retrieval.
     */
    @Transactional
    fun processLecture(lectureId: UUID): Lecture {
        val lecture = lectureRepository.findById(lectureId)
            .orElseThrow { IllegalArgumentException("Lecture not found: $lectureId") }

        if (lecture.transcript.isNullOrBlank()) {
            throw IllegalStateException("Lecture has no transcript to process")
        }

        logger.info("Processing lecture: ${lecture.id} - ${lecture.title}")

        try {
            // Update status to processing
            val processingLecture = lecture.copy(
                status = LectureStatus.PROCESSING,
                updatedAt = Instant.now()
            )
            lectureRepository.save(processingLecture)

            // 1. Chunk the transcript
            val chunks = textChunkingService.chunkBySentences(lecture.transcript!!)
            logger.debug("Created ${chunks.size} chunks from transcript")

            // Save chunks
            val chunkEntities = chunks.map { chunk ->
                LectureChunk(
                    lecture = processingLecture,
                    chunkText = chunk.text,
                    chunkIndex = chunk.index,
                    tokenStart = chunk.tokenStart,
                    tokenEnd = chunk.tokenEnd
                )
            }
            lectureChunkRepository.saveAll(chunkEntities)

            // 2. Extract themes from the full transcript
            val extractedThemes = themeExtractionService.extractThemes(lecture.transcript!!)
            logger.info("Extracted ${extractedThemes.size} themes from lecture")

            // Save themes
            val themeEntities = themeExtractionService.toThemeEntities(extractedThemes, processingLecture)
            themeRepository.saveAll(themeEntities)

            // 3. Update lecture status to completed
            val completedLecture = processingLecture.copy(
                status = LectureStatus.COMPLETED,
                updatedAt = Instant.now()
            )
            return lectureRepository.save(completedLecture)

        } catch (e: Exception) {
            logger.error("Failed to process lecture ${lecture.id}: ${e.message}", e)
            val failedLecture = lecture.copy(
                status = LectureStatus.FAILED,
                errorMessage = e.message,
                updatedAt = Instant.now()
            )
            return lectureRepository.save(failedLecture)
        }
    }

    /**
     * Get lecture by ID.
     */
    fun getLecture(id: UUID): Lecture? {
        return lectureRepository.findById(id).orElse(null)
    }

    /**
     * Get all lectures with pagination.
     */
    fun getAllLectures(pageable: Pageable): Page<Lecture> {
        return lectureRepository.findAll(pageable)
    }

    /**
     * Get themes for a lecture.
     */
    fun getThemes(lectureId: UUID): List<Theme> {
        return themeRepository.findByLectureId(lectureId)
    }

    /**
     * Get recommended problems for a lecture based on extracted themes.
     */
    fun getRecommendedProblems(
        lectureId: UUID,
        topK: Int = 20,
        pageable: Pageable
    ): Page<ProblemSearchResult> {
        val themes = themeRepository.findByLectureId(lectureId)
        if (themes.isEmpty()) {
            return Page.empty(pageable)
        }

        // Convert Theme entities to ExtractedTheme for the retrieval service
        val extractedThemes = themes.map { theme ->
            ExtractedTheme(
                name = theme.name,
                confidence = theme.confidence ?: 0.5,
                summary = theme.summary ?: "",
                keywords = emptyList(),
                mappedTopic = theme.mappedTopic
            )
        }

        return problemRetrievalService.hybridSearch(extractedThemes, topK, pageable)
    }

    /**
     * Delete a lecture and all associated data.
     */
    @Transactional
    fun deleteLecture(lectureId: UUID) {
        lectureChunkRepository.deleteByLectureId(lectureId)
        themeRepository.deleteByLectureId(lectureId)
        lectureRepository.deleteById(lectureId)
        logger.info("Deleted lecture: $lectureId")
    }

    private fun parseKeywords(keywordsJson: String?): List<String> {
        if (keywordsJson.isNullOrBlank()) return emptyList()
        return try {
            // Simple JSON array parsing
            keywordsJson
                .trim('[', ']')
                .split(",")
                .map { it.trim().trim('"') }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
