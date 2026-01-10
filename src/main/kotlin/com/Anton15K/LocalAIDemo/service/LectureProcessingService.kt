package com.Anton15K.LocalAIDemo.service

import com.Anton15K.LocalAIDemo.domain.*
import com.Anton15K.LocalAIDemo.repository.LectureChunkRepository
import com.Anton15K.LocalAIDemo.repository.LectureRepository
import com.Anton15K.LocalAIDemo.repository.ThemeRepository
import com.Anton15K.LocalAIDemo.service.VideoFrameExtractionService
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
        private val lectureSummarizationService: LectureSummarizationService,
        private val videoFrameExtractionService: VideoFrameExtractionService,
        private val imageToTextService: ImageToTextService,
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
        
        var tempDir: java.io.File? = null
        try {
            logger.info("Starting processing for lecture: ${lecture.title}")
            
            // 1. Extract frames (at most 30)
            val (frames, extractedTempDir) = try {
                videoFrameExtractionService.extractFrames(videoBytes, 30)
            } catch (e: Exception) {
                logger.error("Frame extraction failed for lecture ${lecture.id}, continuing with transcript only: ${e.message}")
                Pair(emptyMap<Long, java.io.File>(), null)
            }
            tempDir = extractedTempDir

            // 2. Transcribe audio
            logger.info("Starting transcription for lecture: ${lecture.title}")
            val transcriptResponse = assemblyAiService.transcribeVideo(videoBytes)
            
            // 3. Get descriptions for frames asynchronously
            val descriptionFutures = frames.mapValues { (_, file) ->
                imageToTextService.describeFrameAsync(file)
            }

            // 4. Wait for descriptions and handle potential failures
            val frameDescriptions = descriptionFutures.mapValues { (timestamp, future) ->
                try {
                    future.get(1, java.util.concurrent.TimeUnit.MINUTES)
                } catch (e: Exception) {
                    logger.error("Failed to get description for frame at $timestamp ms: ${e.message}")
                    null
                }
            }.filterValues { it != null } as Map<Long, String>
            logger.info("Frame descriptions count: ${frameDescriptions.size}")
            logger.info("Frame timestamps: ${frameDescriptions.keys}")
            logger.info("Sentence count: ${transcriptResponse.sentences?.size}")
            // 5. Merge frame descriptions into the transcript
            val mergedTranscript =
                if (frameDescriptions.isNotEmpty() && transcriptResponse.words != null) {
                    mergeTranscriptWithFrames(transcriptResponse, frameDescriptions)
                } else {
                    transcriptResponse.text
                }

            logger.info("Merged Transcript:")
            val updatedLecture = lecture.copy(
                transcript = mergedTranscript,
                updatedAt = Instant.now()
            )
            lectureRepository.save(updatedLecture)
            logger.info("Processing completed for lecture: ${lecture.title}")
        } catch (e: Exception) {
            logger.error("Transcription/Processing failed for lecture ${lecture.id}: ${e.message}", e)
            val failedLecture = lecture.copy(
                status = LectureStatus.FAILED,
                errorMessage = "Processing failed: ${e.message}",
                updatedAt = Instant.now()
            )
            lectureRepository.save(failedLecture)
            throw e
        } finally {
            // Cleanup: delete frames and temp directory
            try {
                tempDir?.let { dir ->
                    if (dir.exists()) {
                        dir.deleteRecursively()
                        logger.info("Deleted temporary frame directory: ${dir.absolutePath}")
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to delete temporary directory ${tempDir?.absolutePath}: ${e.message}")
            }
        }
    }

    private fun mergeTranscriptWithFrames(
        transcript: AssemblyAiService.TranscriptResponse,
        frameDescriptions: Map<Long, String>
    ): String {
        val words = transcript.words ?: return transcript.text
        if (frameDescriptions.isEmpty()) return transcript.text

        val sortedFrames = frameDescriptions.toSortedMap() // timestamp -> description
        val remainingFrames = sortedFrames.toMutableMap()

        val result = StringBuilder()

        for (word in words) {
            // Insert any frame descriptions that occur before this word
            val iterator = remainingFrames.iterator()
            while (iterator.hasNext()) {
                val (frameTimestamp, description) = iterator.next()
                if (frameTimestamp <= word.start) {
                    result.append(
                        "\n[Visual Content at ${formatTimestamp(frameTimestamp)}: $description]\n"
                    )
                    logger.info(
                        "[Visual Content at ${formatTimestamp(frameTimestamp)}: $description]"
                    )
                    iterator.remove()
                } else {
                    break
                }
            }

            result.append(word.text).append(" ")
        }

        // Append any remaining frame descriptions (after last word)
        for ((frameTimestamp, description) in remainingFrames) {
            result.append(
                "\n[Visual Content at ${formatTimestamp(frameTimestamp)}: $description]\n"
            )
            logger.info(
                "[Visual Content at ${formatTimestamp(frameTimestamp)}: $description]"
            )
        }

        return result.toString().trim()
    }


    private fun formatTimestamp(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
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

            // 3. Generate structured notes (summarization)
            logger.info("Generating structured notes for lecture: ${lecture.id}")
            val structuredNotes = lectureSummarizationService.generateStructuredNotes(lecture.transcript!!)

            // 4. Update lecture status to completed
            val finalLecture = processingLecture.copy(
                status = LectureStatus.COMPLETED,
                structuredContent = structuredNotes,
                updatedAt = Instant.now()
            )
            val savedLecture = lectureRepository.save(finalLecture)
            logger.info("Lecture processing completed for: ${savedLecture.id}. Structured content length: ${savedLecture.structuredContent?.length ?: 0}")
            return savedLecture
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

    

    // Extract themes from chunks using chunk-level extraction and aggregation
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
