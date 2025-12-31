package com.Anton15K.LocalAIDemo.service

import com.Anton15K.LocalAIDemo.domain.Lecture
import com.Anton15K.LocalAIDemo.domain.Theme
import com.Anton15K.LocalAIDemo.repository.ProblemRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/** Data class for extracted theme information. */
data class ExtractedTheme(
        val name: String,
        val confidence: Double,
        val summary: String,
        val keywords: List<String>,
        val mappedTopic: String?
)

/** Service for extracting mathematical themes from lecture transcripts using LLM. */
@Service
class ThemeExtractionService(
        private val chatClient: ChatClient.Builder,
        private val problemRepository: ProblemRepository,
        private val objectMapper: ObjectMapper,
        @Value("\${app.theme-extraction.max-topics-in-prompt:120}")
        private val maxTopicsInPrompt: Int,
        @Value("\${app.theme-extraction.max-transcript-chars:12000}")
        private val maxTranscriptChars: Int,
        @Value("\${app.theme-extraction.max-themes:8}") private val maxThemes: Int,
        @Value("\${app.theme-extraction.max-themes-per-chunk:3}") private val maxThemesPerChunk: Int
) {
    private val logger = LoggerFactory.getLogger(ThemeExtractionService::class.java)

    // Cache topics context to avoid repeated DB calls within a single processing run
    @Volatile private var cachedTopicsContext: String? = null

    /**
     * Extract themes from a single chunk of text. Uses a simplified prompt optimized for shorter
     * text.
     */
    fun extractThemesFromChunk(chunkText: String, chunkIndex: Int): List<ExtractedTheme> {
        logger.debug("Extracting themes from chunk {} (length: {})", chunkIndex, chunkText.length)

        val topicsContext = getTopicsContext()
        val prompt = buildChunkPrompt(chunkText, topicsContext)

        return try {
            val response = chatClient.build().prompt().user(prompt).call().content()

            parseThemeResponse(response ?: "[]")
        } catch (e: Exception) {
            logger.warn("Failed to extract themes from chunk {}: {}", chunkIndex, e.message)
            emptyList()
        }
    }

    /** Get cached topics context, loading from DB if needed. */
    fun getTopicsContext(): String {
        cachedTopicsContext?.let {
            return it
        }

        val availableTopics = problemRepository.findAllTopics()
        val context =
                if (availableTopics.isNotEmpty()) {
                    val effectiveMax = maxTopicsInPrompt.coerceAtLeast(0)
                    val subset =
                            if (effectiveMax == 0) emptyList()
                            else availableTopics.take(effectiveMax)
                    val omitted = (availableTopics.size - subset.size).coerceAtLeast(0)
                    buildString {
                        append("Available math topics in our database (showing ")
                        append(subset.size)
                        append(" of ")
                        append(availableTopics.size)
                        append("):\n")
                        append(subset.joinToString("\n") { "- $it" })
                        if (omitted > 0) {
                            append("\n(")
                            append(omitted)
                            append(
                                    " more topics omitted. If none match well, set mappedTopic to null.)"
                            )
                        }
                    }
                } else {
                    "Common math topics: Algebra, Geometry, Number Theory, Combinatorics, Probability, Calculus, Linear Algebra, Trigonometry"
                }

        cachedTopicsContext = context
        return context
    }

    /** Clear cached topics context (call between processing runs if needed). */
    fun clearTopicsCache() {
        cachedTopicsContext = null
    }

    private fun buildChunkPrompt(chunkText: String, topicsContext: String): String {
        return """
            Identify the main mathematical themes in this lecture segment.

            Disambiguation: vectors/matrices in geometric contexts (2D/3D, distances, angles) -> Geometry.
            vectors/matrices as abstract spaces/transformations -> Linear Algebra.

            $topicsContext

            Return at most $maxThemesPerChunk themes as JSON array:
            [{"name": "...", "confidence": 0.0-1.0, "summary": "...", "keywords": [...], "mappedTopic": "..." or null}]

            TEXT:
            ---
            $chunkText
            ---

            Return ONLY the JSON array.
        """.trimIndent()
    }

    /** Extract themes from lecture transcript text. */
    fun extractThemes(transcript: String): List<ExtractedTheme> {
        logger.info("Extracting themes from transcript of length: ${transcript.length}")

        val trimmedTranscript = truncateTranscript(transcript, maxTranscriptChars)

        // Get available topics from the problem database for mapping
        val availableTopics = problemRepository.findAllTopics()
        val topicsContext =
                if (availableTopics.isNotEmpty()) {
                    val effectiveMax = maxTopicsInPrompt.coerceAtLeast(0)
                    val subset =
                            if (effectiveMax == 0) emptyList()
                            else availableTopics.take(effectiveMax)
                    val omitted = (availableTopics.size - subset.size).coerceAtLeast(0)
                    buildString {
                        append("Available math topics in our database (showing ")
                        append(subset.size)
                        append(" of ")
                        append(availableTopics.size)
                        append("):\n")
                        append(subset.joinToString("\n") { "- $it" })
                        if (omitted > 0) {
                            append("\n(")
                            append(omitted)
                            append(
                                    " more topics omitted from the prompt. If none of the listed topics match well, set mappedTopic to null.)"
                            )
                        }
                    }
                } else {
                    "Common math topics: Algebra, Geometry, Number Theory, Combinatorics, Probability, Calculus, Linear Algebra, Trigonometry"
                }

        val prompt = buildPrompt(trimmedTranscript, topicsContext)

        val response = chatClient.build().prompt().user(prompt).call().content()

        return parseThemeResponse(response ?: "[]")
    }

    private fun buildPrompt(transcript: String, topicsContext: String): String {
        return """
            You are an expert at analyzing educational content and identifying mathematical topics.
            
            Analyze the following lecture transcript and extract the main mathematical themes/topics covered.

                        Important disambiguation rule:
                        - If the transcript mentions vectors/matrices, decide whether the context is primarily:
                            - Geometry / analytic geometry (vectors in 2D/3D space, dot/cross product, projections, distances, angles, coordinate geometry), OR
                            - Linear algebra (vector spaces, bases, linear transformations, matrices as operators, eigenvalues, determinants, rank).
                        Prefer mapping to Geometry when the context includes geometric objects (lines/planes/angles/distances/coordinates) even if the word "vector" appears.
            
            $topicsContext

            Return at most $maxThemes themes.
            
            For each theme you identify, provide:
            1. name: A concise name for the theme/topic
            2. confidence: A score from 0.0 to 1.0 indicating how confident you are this topic is covered
            3. summary: A brief description of how this topic appears in the lecture
            4. keywords: Key terms related to this topic mentioned in the lecture
            5. mappedTopic: The closest matching topic from the available topics list (if applicable). If none match clearly, set mappedTopic to null.
            
            Return your response as a JSON array of objects. Example format:
            [
              {
                "name": "Quadratic Equations",
                "confidence": 0.95,
                "summary": "The lecture covers solving quadratic equations using the quadratic formula",
                "keywords": ["quadratic", "formula", "discriminant", "roots"],
                "mappedTopic": "Algebra"
              }
            ]
            
            LECTURE TRANSCRIPT:
            ---
            $transcript
            ---
            
            Return ONLY the JSON array, no additional text.
        """.trimIndent()
    }

    private fun truncateTranscript(transcript: String, maxChars: Int): String {
        val effectiveMax = maxChars.coerceAtLeast(500)
        if (transcript.length <= effectiveMax) return transcript

        // Keep beginning and end to preserve context; this reduces token load drastically.
        val headSize = (effectiveMax * 0.65).toInt().coerceAtLeast(200)
        val tailSize = (effectiveMax - headSize).coerceAtLeast(200)

        val head = transcript.take(headSize)
        val tail = transcript.takeLast(tailSize)

        return buildString {
            append(head.trimEnd())
            append("\n\n[... transcript truncated for performance ...]\n\n")
            append(tail.trimStart())
        }
    }

    private fun parseThemeResponse(response: String): List<ExtractedTheme> {
        return try {
            // Extract JSON array from response (handle potential markdown code blocks)
            val jsonContent = response.replace("```json", "").replace("```", "").trim()

            objectMapper.readValue<List<ExtractedTheme>>(jsonContent)
        } catch (e: Exception) {
            logger.error("Failed to parse theme extraction response: ${e.message}")
            logger.debug("Raw response: $response")
            emptyList()
        }
    }

    /** Convert extracted themes to Theme entities for a lecture. */
    fun toThemeEntities(extractedThemes: List<ExtractedTheme>, lecture: Lecture): List<Theme> {
        return extractedThemes.map { extracted ->
            Theme(
                    lecture = lecture,
                    name = extracted.name,
                    confidence = extracted.confidence,
                    summary = extracted.summary,
                    mappedTopic = extracted.mappedTopic
            )
        }
    }
}
