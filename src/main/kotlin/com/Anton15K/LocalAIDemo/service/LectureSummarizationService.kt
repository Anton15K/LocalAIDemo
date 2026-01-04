package com.Anton15K.LocalAIDemo.service

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service

/**
 * Service for generating structured, easy-to-read study notes from math lecture transcripts.
 * Ensures all information is preserved and math is formatted using LaTeX.
 */
@Service
class LectureSummarizationService(private val chatClient: ChatClient.Builder) {
    private val logger = LoggerFactory.getLogger(LectureSummarizationService::class.java)

    fun generateStructuredNotes(transcript: String): String {
        logger.info("Generating structured notes for transcript (length: ${transcript.length})")
        
        if (transcript.isBlank()) {
            logger.warn("Transcript is blank, returning placeholder.")
            return "No transcript available to generate notes."
        }
        
        val prompt = """
            You are an expert math professor's assistant. Your task is to transform the following raw lecture transcript into a well-structured, easy-to-read, and professional set of study notes in Markdown format.
            
            CRITICAL REQUIREMENTS:
            1. COVER EVERYTHING: Do NOT lose ANY information from the lecture. Every concept, example, proof, and key point mentioned must be included in the notes.
            2. Markdown FORMAT: Use Markdown headers (###), bullet points, and bold text for structure.
            3. MATH FORMULAS: Whenever you write a mathematical formula, equation, or variable, wrap it in LaTeX delimiters.
               - Use ${'$'} ... ${'$'} for inline math (e.g., ${'$'}f(x) = x^2${'$'}).
               - Use ${'$'}${'$'} ... ${'$'}${'$'} for block equations.
            4. CLARITY: Fix grammatical errors and improve the flow of the transcript while strictly preserving the technical meaning and all details.
            5. COMPLETENESS: If the lecturer provides an example or a step-by-step derivation, include it fully.
            
            LECTURE TRANSCRIPT:
            ---
            $transcript
            ---
            
            Produce the full, comprehensive study notes in Markdown now.
        """.trimIndent()

        return try {
            val response = chatClient.build()
                .prompt()
                .user(prompt)
                .call()
                .content()
            
            if (response.isNullOrBlank()) {
                logger.warn("LLM returned empty or null response for summarization.")
                return "Failed to generate notes: LLM returned an empty response."
            }

            logger.info("Successfully generated structured notes (length: ${response.length}).")
            response
        } catch (e: Exception) {
            logger.error("Error generating structured notes: ${e.message}", e)
            "Failed to generate notes due to an error: ${e.message}"
        }
    }
}
