package com.Anton15K.LocalAIDemo.controller

import com.Anton15K.LocalAIDemo.dto.ComponentHealth
import com.Anton15K.LocalAIDemo.dto.HealthResponse
import com.Anton15K.LocalAIDemo.repository.ProblemRepository
import org.springframework.ai.chat.client.ChatClient
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.sql.DataSource

@RestController
@RequestMapping("/api/health")
class HealthController(
    private val dataSource: DataSource,
    private val problemRepository: ProblemRepository,
    private val chatClientBuilder: ChatClient.Builder
) {

    @GetMapping
    fun health(): ResponseEntity<HealthResponse> {
        val components = mutableMapOf<String, ComponentHealth>()
        var overallStatus = "UP"

        // Check database
        try {
            dataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT 1").use { rs ->
                        if (rs.next()) {
                            val problemCount = problemRepository.count()
                            components["database"] = ComponentHealth(
                                status = "UP",
                                details = mapOf(
                                    "type" to "PostgreSQL with pgvector",
                                    "problemCount" to problemCount
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            overallStatus = "DOWN"
            components["database"] = ComponentHealth(
                status = "DOWN",
                details = mapOf("error" to (e.message ?: "Unknown error"))
            )
        }

        // Check Ollama
        try {
            val response = chatClientBuilder.build()
                .prompt()
                .user("Say 'ok' in one word")
                .call()
                .content()
            
            if (response != null) {
                components["ollama"] = ComponentHealth(
                    status = "UP",
                    details = mapOf("model" to "available")
                )
            } else {
                overallStatus = "DEGRADED"
                components["ollama"] = ComponentHealth(
                    status = "DEGRADED",
                    details = mapOf("warning" to "Empty response from model")
                )
            }
        } catch (e: Exception) {
            overallStatus = if (overallStatus == "UP") "DEGRADED" else overallStatus
            components["ollama"] = ComponentHealth(
                status = "DOWN",
                details = mapOf("error" to (e.message ?: "Cannot connect to Ollama"))
            )
        }

        return ResponseEntity.ok(HealthResponse(
            status = overallStatus,
            components = components
        ))
    }

    @GetMapping("/live")
    fun liveness(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("status" to "UP"))
    }

    @GetMapping("/ready")
    fun readiness(): ResponseEntity<Map<String, String>> {
        // Check critical dependencies
        return try {
            dataSource.connection.use { it.isValid(2) }
            ResponseEntity.ok(mapOf("status" to "UP"))
        } catch (e: Exception) {
            ResponseEntity.status(503).body(mapOf("status" to "DOWN"))
        }
    }
}
