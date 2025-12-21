package com.Anton15K.LocalAIDemo.service

import com.Anton15K.LocalAIDemo.domain.Lecture
import com.Anton15K.LocalAIDemo.domain.Theme
import com.Anton15K.LocalAIDemo.repository.ProblemRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
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
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(ThemeExtractionService::class.java)

    /**
     * Extract themes from lecture transcript text.
     */
    fun extractThemes(transcript: String): List<ExtractedTheme> {
        logger.info("Extracting themes from transcript of length: ${transcript.length}")
        
        // Get available topics from the problem database for mapping
        val availableTopics = problemRepository.findAllTopics()
        val topicsContext = if (availableTopics.isNotEmpty()) {
            "Available math topics in our database: ${availableTopics.joinToString(", ")}"
        } else {
            "Common math topics: Algebra, Geometry, Number Theory, Combinatorics, Probability, Calculus, Linear Algebra, Trigonometry"
        }

        val prompt = buildPrompt(transcript, topicsContext)
        
        val response = chatClient.build()
            .prompt()
            .user(prompt)
            .call()
            .content()

        return parseThemeResponse(response ?: "[]")
    }

    private fun buildPrompt(transcript: String, topicsContext: String): String {
        return """
            You are an expert at analyzing educational content and identifying mathematical topics.
            
            Analyze the following lecture transcript and extract the main mathematical themes/topics covered.
            
            $topicsContext
            
            For each theme you identify, provide:
            1. name: A concise name for the theme/topic
            2. confidence: A score from 0.0 to 1.0 indicating how confident you are this topic is covered
            3. summary: A brief description of how this topic appears in the lecture
            4. keywords: Key terms related to this topic mentioned in the lecture
            5. mappedTopic: The closest matching topic from the available topics list (if applicable)
            
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

    private fun parseThemeResponse(response: String): List<ExtractedTheme> {
        return try {
            // Extract JSON array from response (handle potential markdown code blocks)
            val jsonContent = response
                .replace("```json", "")
                .replace("```", "")
                .trim()
            
            objectMapper.readValue<List<ExtractedTheme>>(jsonContent)
        } catch (e: Exception) {
            logger.error("Failed to parse theme extraction response: ${e.message}")
            logger.debug("Raw response: $response")
            emptyList()
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
                keywords = objectMapper.writeValueAsString(extracted.keywords),
                mappedTopic = extracted.mappedTopic
            )
        }
    }
}
