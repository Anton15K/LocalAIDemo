package com.Anton15K.LocalAIDemo.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.io.File
import java.util.Base64
import java.util.concurrent.CompletableFuture

@Service
class ImageToTextService(
    @Value("\${openai.api.key}") private val apiKey: String
) {

    private val logger = LoggerFactory.getLogger(ImageToTextService::class.java)
    private val restTemplate = RestTemplate()

    private val baseUrl = "https://api.openai.com/v1/chat/completions"
    private val modelName = "gpt-4.1-nano"

    fun describeFrameAsync(frameFile: File): CompletableFuture<String> =
        CompletableFuture.supplyAsync { describeFrame(frameFile) }

    fun describeFrame(frameFile: File): String {
        if (apiKey.isBlank()) {
            throw IllegalStateException("OpenAI API key is not configured")
        }

        return try {
            logger.info("Describing frame: ${frameFile.name} using $modelName")

            val imageBase64 = Base64.getEncoder()
                .encodeToString(frameFile.readBytes())

            val prompt = """
                Describe what is happening in this video frame from a math lecture.
                Focus on:
                - formulas
                - diagrams
                - text written on the board

                Use Markdown math notation:
                - Inline: $...$
                - Block: $$...$$
            """.trimIndent()

            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            headers.setBearerAuth(apiKey)

            val body = mapOf(
                "model" to modelName,
                "messages" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to listOf(
                            mapOf(
                                "type" to "text",
                                "text" to prompt
                            ),
                            mapOf(
                                "type" to "image_url",
                                "image_url" to mapOf(
                                    "url" to "data:image/jpeg;base64,$imageBase64"
                                )
                            )
                        )
                    )
                )
            )


            val request = HttpEntity(body, headers)

            val response = restTemplate.postForEntity(
                baseUrl,
                request,
                Map::class.java
            )

            extractText(response.body)
        } catch (e: Exception) {
            logger.error("Image-to-text failed: ${e.message}", e)
            "[Error describing frame: ${e.message}]"
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractText(response: Map<*, *>?): String {
        if (response == null) return "No response from OpenAI."

        val choices = response["choices"] as? List<Map<String, Any>>
            ?: return "No choices in response."

        val message = choices.first()["message"] as? Map<String, Any>
            ?: return "No message in response."

        val content = message["content"]

        return when (content) {
            is String -> content
            is List<*> -> {
                content
                    .filterIsInstance<Map<String, Any>>()
                    .firstOrNull { it["type"] == "output_text" }
                    ?.get("text") as? String
                    ?: "No text output."
            }
            else -> "Unexpected response format."
        }
    }
}
