package com.Anton15K.LocalAIDemo.service

import com.Anton15K.LocalAIDemo.domain.*
import com.Anton15K.LocalAIDemo.repository.LectureChunkRepository
import com.Anton15K.LocalAIDemo.repository.LectureRepository
import com.Anton15K.LocalAIDemo.repository.ThemeRepository
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Main service for processing lectures: chunking, theme extraction, and problem retrieval. */
@Service
class LectureProcessingService(
        private val lectureRepository: LectureRepository,
        private val lectureChunkRepository: LectureChunkRepository,
        private val themeRepository: ThemeRepository,
        private val textChunkingService: TextChunkingService,
        private val themeExtractionService: ThemeExtractionService,
        private val chunkThemeAggregationService: ChunkThemeAggregationService,
    private val themeExtractionTuningService: ThemeExtractionTuningService,
        private val topicMappingService: TopicMappingService,
        private val problemRetrievalService: ProblemRetrievalService,
        private val assemblyAiService: AssemblyAiService,
        @org.springframework.beans.factory.annotation.Value(
                "\${app.theme-extraction.chunk-level-enabled:true}"
        )
        private val chunkLevelEnabled: Boolean
) {
    private val logger = LoggerFactory.getLogger(LectureProcessingService::class.java)

    /** Create a new lecture from transcript text. */
    @Transactional
    fun createLecture(title: String, transcript: String, uploadedBy: String? = null): Lecture {
        val lecture =
                Lecture(
                        title = title,
                        transcript = transcript,
                        uploadedBy = uploadedBy,
                        status = LectureStatus.PENDING,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now()
                )
        return lectureRepository.save(lecture)
    }

    /** Create a new lecture from a video file. */
    @Transactional
    fun createLectureFromVideo(title: String, videoBytes: ByteArray, uploadedBy: String? = null): Lecture {
        val lecture = Lecture(
            title = title,
            transcript = null, // Will be filled after transcription
            uploadedBy = uploadedBy,
            status = LectureStatus.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val savedLecture = lectureRepository.save(lecture)
        
        // We will store the bytes in a way that transcription can access it, 
        // but for now let's keep it simple and just return the lecture.
        // The actual transcription should happen in a separate step or background.
        return savedLecture
    }

    /** Transcribe video and update lecture transcript. */
    @Transactional
    fun transcribeVideoForLecture(lectureId: UUID, videoBytes: ByteArray) {
        val lecture = lectureRepository.findById(lectureId).orElseThrow {
            IllegalArgumentException("Lecture not found: $lectureId")
        }
        
        try {
            logger.info("Starting transcription for lecture: ${lecture.title}")
            val transcript = assemblyAiService.transcribeVideo(videoBytes)
            
            val updatedLecture = lecture.copy(
                transcript = transcript,
                updatedAt = Instant.now()
            )
            lectureRepository.save(updatedLecture)
            logger.info("Transcription completed for lecture: ${lecture.title}")
        } catch (e: Exception) {
            logger.error("Transcription failed for lecture ${lecture.id}: ${e.message}", e)
            val failedLecture = lecture.copy(
                status = LectureStatus.FAILED,
                errorMessage = "Transcription failed: ${e.message}",
                updatedAt = Instant.now()
            )
            lectureRepository.save(failedLecture)
            throw e
        }
    }

    /** Process a lecture: chunk transcript, extract themes, and prepare for retrieval. */
    @Transactional
    fun processLecture(
            lectureId: UUID,
            tuningLevel: Int? = null,
            lectureMinutes: Int? = null
    ): Lecture {
        val lecture =
                lectureRepository.findById(lectureId).orElseThrow {
                    IllegalArgumentException("Lecture not found: $lectureId")
                }

        if (lecture.transcript.isNullOrBlank()) {
            throw IllegalStateException("Lecture has no transcript to process")
        }

        logger.info("Processing lecture: ${lecture.id} - ${lecture.title}")

        try {
            // Update status to processing
            val processingLecture =
                    lecture.copy(status = LectureStatus.PROCESSING, updatedAt = Instant.now())
            lectureRepository.save(processingLecture)

                val tuning = themeExtractionTuningService.tune(
                    transcript = lecture.transcript!!,
                    request = ThemeExtractionTuningService.TuningRequest(
                        granularityLevel = tuningLevel,
                        lectureMinutes = lectureMinutes
                    )
                )

                // 1. Chunk the transcript
                val chunks =
                    if (tuning.chunkLevelEnabled) {
                    textChunkingService.chunkSemantically(
                        lecture.transcript!!,
                        targetChunkSize = tuning.chunkSizeWords
                    )
                    } else {
                    listOf(TextChunk(text = lecture.transcript!!, index = 0, tokenStart = 0, tokenEnd = 0))
                    }
            logger.debug("Created ${chunks.size} chunks from transcript")

            // Save chunks
            val chunkEntities =
                    chunks.map { chunk ->
                        LectureChunk(
                                lecture = processingLecture,
                                chunkText = chunk.text,
                                chunkIndex = chunk.index,
                                tokenStart = chunk.tokenStart,
                                tokenEnd = chunk.tokenEnd
                        )
                    }
            lectureChunkRepository.saveAll(chunkEntities)

            // 2. Extract themes (chunk-level or full transcript)
            val extractedThemes =
                    if (chunkLevelEnabled && tuning.chunkLevelEnabled && chunks.size > 1) {
                        extractThemesFromChunks(chunks, tuning)
                    } else {
                        topicMappingService.mapThemesToExistingTopics(
                        themeExtractionService.extractThemes(
                            lecture.transcript!!,
                            maxThemesOverride = tuning.maxFinalThemes
                        )
                        )
                    }
            logger.info("Extracted ${extractedThemes.size} themes from lecture")

            // Save themes
            val themeEntities =
                    themeExtractionService.toThemeEntities(extractedThemes, processingLecture)
            themeRepository.saveAll(themeEntities)

            // 3. Update lecture status to completed
            val completedLecture =
                    processingLecture.copy(
                            status = LectureStatus.COMPLETED,
                            updatedAt = Instant.now()
                    )
            return lectureRepository.save(completedLecture)
        } catch (e: Exception) {
            logger.error("Failed to process lecture ${lecture.id}: ${e.message}", e)
            val failedLecture =
                    lecture.copy(
                            status = LectureStatus.FAILED,
                            errorMessage = e.message,
                            updatedAt = Instant.now()
                    )
            return lectureRepository.save(failedLecture)
        }
    }

    /** Get lecture by ID. */
    fun getLecture(id: UUID): Lecture? {
        return lectureRepository.findById(id).orElse(null)
    }

    /** Get all lectures with pagination. */
    fun getAllLectures(pageable: Pageable): Page<Lecture> {
        return lectureRepository.findAll(pageable)
    }

    /** Get themes for a lecture. */
    fun getThemes(lectureId: UUID): List<Theme> {
        return themeRepository.findByLectureId(lectureId)
    }

    /** Get recommended problems for a lecture based on extracted themes. */
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
        val extractedThemes =
                themes.map { theme ->
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

    /** Delete a lecture and all associated data. */
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
            keywordsJson.trim('[', ']').split(",").map { it.trim().trim('"') }.filter {
                it.isNotBlank()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Extract themes from individual chunks, then aggregate. */
    private fun extractThemesFromChunks(
            chunks: List<TextChunk>,
            tuning: ThemeExtractionTuningService.TunedThemeExtractionSettings
    ): List<ExtractedTheme> {
        logger.info("Extracting themes from {} chunks (chunk-level mode)", chunks.size)

        // Extract themes from each chunk
        val chunkThemes =
                chunks.mapIndexed { index, chunk ->
                themeExtractionService.extractThemesFromChunk(
                    chunkText = chunk.text,
                    chunkIndex = index,
                    maxThemesPerChunkOverride = tuning.maxThemesPerChunk
                )
                }

        // Aggregate across chunks (filters by occurrence threshold)
        val aggregated =
            chunkThemeAggregationService.aggregateThemes(
                chunkThemes,
                minChunkOccurrencesOverride = tuning.minChunkOccurrences,
                minOccurrenceRatioOverride = tuning.minOccurrenceRatio,
                maxFinalThemesOverride = tuning.maxFinalThemes
            )

        // Clear topics cache after processing
        themeExtractionService.clearTopicsCache()

        // Map to existing topics
        return topicMappingService.mapThemesToExistingTopics(aggregated)
    }
}
