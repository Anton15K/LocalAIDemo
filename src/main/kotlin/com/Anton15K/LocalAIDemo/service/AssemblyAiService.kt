package com.Anton15K.LocalAIDemo.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.util.concurrent.TimeUnit

@Service
class AssemblyAiService(
    @Value("\${app.assemblyai.api-key}") private val apiKey: String,
    @Value("\${app.assemblyai.base-url}") private val baseUrl: String
) {
    private val logger = LoggerFactory.getLogger(AssemblyAiService::class.java)
    private val restTemplate = RestTemplate()

    /** Runs full transcription pipeline for a video file. */
    fun transcribeVideo(fileBytes: ByteArray): String {
        if (apiKey.isBlank()) {
            throw IllegalStateException("AssemblyAI API key is not configured")
        }

        // 1. Upload the file
        val uploadUrl = uploadFile(fileBytes)
        logger.info("File uploaded to AssemblyAI: $uploadUrl")

        // 2. Start transcription
        val transcriptId = startTranscription(uploadUrl)
        logger.info("Transcription started with ID: $transcriptId")

        // 3. Poll for completion
        return pollForTranscript(transcriptId)
    }
    /** Upload file to AssemblyAI and return upload URL. */
    private fun uploadFile(fileBytes: ByteArray): String {
        val headers = HttpHeaders()
        headers.set("Authorization", apiKey)
        headers.contentType = MediaType.APPLICATION_OCTET_STREAM

        val request = HttpEntity(fileBytes, headers)
        val response = restTemplate.postForEntity("$baseUrl/upload", request, Map::class.java)

        return response.body?.get("upload_url") as? String
            ?: throw RuntimeException("Failed to upload file to AssemblyAI")
    }

    /** Start transcription for the given URL. */
    private fun startTranscription(audioUrl: String): String {
        val headers = HttpHeaders()
        headers.set("Authorization", apiKey)
        headers.contentType = MediaType.APPLICATION_JSON

        val body = mapOf("audio_url" to audioUrl)
        val request = HttpEntity(body, headers)

        val response = restTemplate.postForEntity("$baseUrl/transcript", request, Map::class.java)

        return response.body?.get("id") as? String
            ?: throw RuntimeException("Failed to start transcription")
    }

    /** Get transcript text for the given ID. */
    private fun pollForTranscript(transcriptId: String): String {
        val headers = HttpHeaders()
        headers.set("Authorization", apiKey)
        val request = HttpEntity<Unit>(headers)

        while (true) {
            val response = restTemplate.exchange(
                "$baseUrl/transcript/$transcriptId",
                org.springframework.http.HttpMethod.GET,
                request,
                Map::class.java
            )

            val status = response.body?.get("status") as? String
            logger.debug("Transcription status: $status")

            when (status) {
                "completed" -> return response.body?.get("text") as String
                "error" -> {
                    val error = response.body?.get("error") as? String
                    throw RuntimeException("Transcription failed: $error")
                }
                else -> {
                    TimeUnit.SECONDS.sleep(5)
                }
            }
        }
    }
}
