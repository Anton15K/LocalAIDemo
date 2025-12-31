package com.Anton15K.LocalAIDemo.controller

import com.Anton15K.LocalAIDemo.repository.ProblemRepository
import com.Anton15K.LocalAIDemo.service.ProblemRetrievalService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

@RestController
@RequestMapping("/api/admin/vector-store")
class AdminVectorStoreController(
    private val jdbcTemplate: JdbcTemplate,
    private val problemRepository: ProblemRepository,
    private val problemRetrievalService: ProblemRetrievalService
) {
    private val logger = LoggerFactory.getLogger(AdminVectorStoreController::class.java)

    private val isRunning = AtomicBoolean(false)

    @Volatile
    private var status: VectorStoreReindexStatus = VectorStoreReindexStatus(idle = true)

    data class VectorStoreReindexStatus(
        val idle: Boolean,
        val running: Boolean = false,
        val startedAt: Instant? = null,
        val lastUpdatedAt: Instant? = null,
        val totalProblems: Long? = null,
        val indexedProblems: Long = 0,
        val batchSize: Int? = null,
        val delayMs: Long? = null,
        val vectorStoreCount: Long? = null,
        val lastError: String? = null
    )

    @GetMapping("/status")
    fun getStatus(): VectorStoreReindexStatus {
        val vectorStoreCount = try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vector_store", Long::class.java)
        } catch (_: Exception) {
            null
        }

        return status.copy(vectorStoreCount = vectorStoreCount, lastUpdatedAt = Instant.now())
    }

    /**
     * Clear all embeddings in the vector store.
     */
    @PostMapping("/clear")
    fun clear(): ResponseEntity<Map<String, Any>> {
        if (isRunning.get()) {
            return ResponseEntity.status(409).body(mapOf("status" to "already_running"))
        }

        jdbcTemplate.execute("TRUNCATE TABLE vector_store")
        status = VectorStoreReindexStatus(idle = true, vectorStoreCount = 0, lastUpdatedAt = Instant.now())
        return ResponseEntity.ok(mapOf("status" to "cleared"))
    }

    /**
     * Rebuild problem embeddings in the vector store.
     *
     * This is required after changing `spring.ai.ollama.embedding.options.model`.
     *
     * NOTE: This truncates the whole `vector_store` table.
     */
    @PostMapping("/reindex-problems")
    fun reindexProblems(
        @RequestParam(defaultValue = "200") batchSize: Int,
        @RequestParam(defaultValue = "0") delayMs: Long
    ): ResponseEntity<Map<String, Any>> {
        if (!isRunning.compareAndSet(false, true)) {
            return ResponseEntity.status(409).body(mapOf("status" to "already_running"))
        }

        val effectiveBatchSize = batchSize.coerceIn(1, 2000)
        val effectiveDelayMs = delayMs.coerceAtLeast(0)

        val startedAt = Instant.now()
        status = VectorStoreReindexStatus(
            idle = false,
            running = true,
            startedAt = startedAt,
            lastUpdatedAt = startedAt,
            totalProblems = problemRepository.count(),
            indexedProblems = 0,
            batchSize = effectiveBatchSize,
            delayMs = effectiveDelayMs,
            lastError = null
        )

        Thread(
            {
                try {
                    val total = status.totalProblems ?: problemRepository.count()
                    logger.info("Vector store reindex started: totalProblems=$total batchSize=$effectiveBatchSize delayMs=$effectiveDelayMs")

                    jdbcTemplate.execute("TRUNCATE TABLE vector_store")

                    var page = 0
                    var indexed = 0L
                    while (true) {
                        val problems = problemRepository.findAll(PageRequest.of(page, effectiveBatchSize)).content
                        if (problems.isEmpty()) break

                        problemRetrievalService.indexProblems(problems)
                        indexed += problems.size.toLong()
                        status = status.copy(indexedProblems = indexed, lastUpdatedAt = Instant.now())

                        if (effectiveDelayMs > 0) {
                            Thread.sleep(effectiveDelayMs)
                        }

                        if (page % 10 == 0) {
                            logger.info("Vector store reindex progress: indexed=$indexed/$total")
                        }

                        page += 1
                    }

                    status = status.copy(running = false, lastUpdatedAt = Instant.now())
                    logger.info("Vector store reindex finished: indexed=$indexed")
                } catch (e: Exception) {
                    status = status.copy(running = false, lastUpdatedAt = Instant.now(), lastError = e.message)
                    logger.error("Vector store reindex failed: ${e.message}", e)
                } finally {
                    isRunning.set(false)
                    status = status.copy(running = false, lastUpdatedAt = Instant.now())
                }
            },
            "vector-store-reindex"
        ).start()

        return ResponseEntity.accepted().body(
            mapOf(
                "status" to "started",
                "batchSize" to effectiveBatchSize,
                "delayMs" to effectiveDelayMs,
                "note" to "This truncates vector_store and rebuilds embeddings for all problems. Watch logs for progress."
            )
        )
    }
}
