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

/**
 * Data class for extracted theme information.
 */
data class ExtractedTheme(
    val name: String,
    val confidence: Double,
    val summary: String,
    val keywords: List<String>,
    val mappedTopic: String?
)

/**
 * Service for extracting mathematical themes from lecture transcripts using LLM.
 */
@Service
class ThemeExtractionService(
    private val chatClient: ChatClient.Builder,
    private val problemRepository: ProblemRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${app.theme-extraction.max-topics-in-prompt:120}")
    private val maxTopicsInPrompt: Int,
    @Value("\${app.theme-extraction.max-transcript-chars:12000}")
    private val maxTranscriptChars: Int,
    @Value("\${app.theme-extraction.max-themes:8}")
    private val maxThemes: Int
) {
    private val logger = LoggerFactory.getLogger(ThemeExtractionService::class.java)

    /**
     * Extract themes from lecture transcript text.
     */
    fun extractThemes(transcript: String): List<ExtractedTheme> {
        logger.info("Extracting themes from transcript of length: ${transcript.length}")

        val trimmedTranscript = truncateTranscript(transcript, maxTranscriptChars)
        
        // Get available topics from the problem database for mapping
        val availableTopics = problemRepository.findAllTopics()
        val topicsContext = if (availableTopics.isNotEmpty()) {
            val effectiveMax = maxTopicsInPrompt.coerceAtLeast(0)
            val subset = if (effectiveMax == 0) emptyList() else availableTopics.take(effectiveMax)
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
                    append(" more topics omitted from the prompt. If none of the listed topics match well, set mappedTopic to null.)")
                }
            }
        } else {
            "Common math topics: Algebra, Geometry, Number Theory, Combinatorics, Probability, Calculus, Linear Algebra, Trigonometry"
        }

        val prompt = buildPrompt(trimmedTranscript, topicsContext)
        logger.debug("Generated prompt for theme extraction (length: ${prompt.length} chars)")
        
        val response = try {
            chatClient.build()
                .prompt()
                .user(prompt)
                .call()
                .content()
        } catch (e: Exception) {
            logger.error("Failed to call LLM for theme extraction: ${e.message}", e)
            throw RuntimeException("Theme extraction failed: Unable to connect to LLM service. Please check that Ollama is running and the model '${getModelName()}' is available.", e)
        }

        if (response.isNullOrBlank()) {
            logger.warn("LLM returned empty response for theme extraction")
            throw RuntimeException("Theme extraction failed: LLM returned empty response. Please check Ollama service and model availability.")
        }
        
        logger.debug("LLM response received (length: ${response.length} chars)")
        return parseThemeResponse(response)
    }
    
    private fun getModelName(): String {
        // This would ideally come from configuration, but we'll use a placeholder for now
        return "configured chat model"
    }

    private fun buildPrompt(transcript: String, topicsContext: String): String {
        return """
            You are analyzing a mathematics lecture transcript to identify which mathematical TOPICS are being taught.
            
            Your goal: Identify the main mathematical subjects/topics covered in the lecture.
            
            DO NOT:
            - Extract specific problems, exercises, or calculations from the transcript
            - Extract specific points, coordinates, or numerical values
            - List individual tasks or questions from the transcript
            
            DO:
            - Identify the broad mathematical topics being taught (e.g., "Vectors", "Calculus", "Algebra")
            - Determine the mathematical subjects and concepts being explained

            Important disambiguation rule for vectors and matrices:
            - Choose GEOMETRY if the lecture discusses vectors as arrows/directed segments in 2D/3D space, geometric properties (distances, angles, projections), coordinate geometry, or spatial relationships.
            - Choose LINEAR ALGEBRA only if the lecture discusses abstract vector spaces, linear transformations as functions, eigenvalues/eigenvectors, basis/span, or theoretical algebraic properties.
            - When in doubt with vectors, prefer GEOMETRY over LINEAR ALGEBRA.
            
            $topicsContext

            Identify at most $maxThemes mathematical topics from the transcript below.
            
            You MUST return a JSON array where each object has EXACTLY these 5 fields:
            - name: the topic name (string)
            - confidence: your confidence score (number between 0.0 and 1.0)
            - summary: brief description (string)
            - keywords: list of related terms (array of strings)
            - mappedTopic: closest match from available topics, or null (string or null)
            
            Example of correct format:
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
            
            Return ONLY the JSON array. No other text before or after.
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
            val jsonContent = response
                .replace("```json", "")
                .replace("```", "")
                .trim()
            
            if (jsonContent.isEmpty() || jsonContent == "[]") {
                logger.warn("LLM returned empty JSON array - no themes extracted")
                return emptyList()
            }
            
            logger.debug("Attempting to parse JSON response: ${jsonContent.take(200)}...")
            val themes = objectMapper.readValue<List<ExtractedTheme>>(jsonContent)
            logger.info("Successfully parsed ${themes.size} themes from LLM response")
            themes
        } catch (e: Exception) {
            logger.error("Failed to parse theme extraction response: ${e.message}", e)
            logger.error("Raw response (first 1000 chars): ${response.take(1000)}")
            val errorMsg = buildString {
                append("Theme extraction failed: The LLM returned an invalid response format. ")
                append("Expected JSON with fields: name, confidence, summary, keywords, mappedTopic. ")
                append("Instead received: ${response.take(200)}... ")
                append("This usually means the model is not following the prompt instructions correctly. ")
                append("Try a different model (e.g., mistral, llama3) or lower the temperature to 0.3.")
            }
            throw RuntimeException(errorMsg, e)
        }
    }

    /**
     * Convert extracted themes to Theme entities for a lecture.
     */
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
