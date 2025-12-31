package com.Anton15K.LocalAIDemo.service

import com.Anton15K.LocalAIDemo.repository.ProblemRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import kotlin.math.roundToInt
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

@Service
class DeepMathIngestionJobService(
    private val objectMapper: ObjectMapper,
    private val mathDatasetIngestionService: MathDatasetIngestionService,
    private val problemRepository: ProblemRepository,
    restClientBuilder: RestClient.Builder,
    @Value("\${app.ingestion.deepmath.dataset:zwhe99/DeepMath-103K}")
    private val defaultDataset: String,
    @Value("\${app.ingestion.deepmath.config:default}")
    private val defaultConfig: String,
    @Value("\${app.ingestion.deepmath.split:train}")
    private val defaultSplit: String,
    @Value("\${app.ingestion.deepmath.batch-size:100}")
    private val defaultBatchSize: Int,
    @Value("\${app.ingestion.deepmath.max-rows:0}")
    private val defaultMaxRows: Int,
    @Value("\${app.ingestion.deepmath.index-embeddings:false}")
    private val defaultIndexEmbeddings: Boolean,
    @Value("\${app.ingestion.deepmath.request-delay-ms:250}")
    private val defaultRequestDelayMs: Long,
    @Value("\${app.ingestion.deepmath.index-chunk-size:100}")
    private val defaultIndexChunkSize: Int,
    @Value("\${app.ingestion.deepmath.index-delay-ms:200}")
    private val defaultIndexDelayMs: Long,
    @Value("\${app.huggingface.token:}")
    private val huggingFaceToken: String?
) {
    private val logger = LoggerFactory.getLogger(DeepMathIngestionJobService::class.java)

    private val restClient: RestClient = restClientBuilder
        .baseUrl("https://datasets-server.huggingface.co")
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build()

    private val running = AtomicBoolean(false)

    @Volatile
    private var status: DeepMathIngestionStatus = DeepMathIngestionStatus(idle = true)

    companion object {
        // Hugging Face datasets-server enforces length <= 100 for /rows
        private const val MAX_ROWS_LENGTH = 100
    }

    data class StartResult(
        val started: Boolean,
        val reason: String? = null
    )

    data class DeepMathIngestionStatus(
        val idle: Boolean,
        val running: Boolean = false,
        val startedAt: Instant? = null,
        val lastUpdatedAt: Instant? = null,
        val dataset: String? = null,
        val config: String? = null,
        val split: String? = null,
        val batchSize: Int? = null,
        val maxRows: Int? = null,
        val requestDelayMs: Long? = null,
        val indexEmbeddings: Boolean? = null,
        val indexChunkSize: Int? = null,
        val indexDelayMs: Long? = null,
        val tokenPresent: Boolean = false,
        val totalRows: Int? = null,
        val effectiveTotal: Int? = null,
        val processedRows: Int = 0,
        val imported: Int = 0,
        val skipped: Int = 0,
        val failed: Int = 0,
        val lastError: String? = null
    )

    fun getStatus(): DeepMathIngestionStatus = status

    fun startFromConfiguredDefaults(): StartResult {
        return start(
            dataset = defaultDataset,
            config = defaultConfig,
            split = defaultSplit,
            batchSize = defaultBatchSize,
            maxRows = defaultMaxRows,
            requestDelayMs = defaultRequestDelayMs,
            indexEmbeddings = defaultIndexEmbeddings,
            indexChunkSize = defaultIndexChunkSize,
            indexDelayMs = defaultIndexDelayMs
        )
    }

    fun start(
        dataset: String,
        config: String,
        split: String,
        batchSize: Int,
        maxRows: Int,
        requestDelayMs: Long,
        indexEmbeddings: Boolean,
        indexChunkSize: Int,
        indexDelayMs: Long
    ): StartResult {
        if (!running.compareAndSet(false, true)) {
            return StartResult(started = false, reason = "already_running")
        }

        val startedAt = Instant.now()
        val effectiveBatchSize = batchSize.coerceAtLeast(1).coerceAtMost(MAX_ROWS_LENGTH)
        val effectiveMaxRows = maxRows.coerceAtLeast(0)

        status = DeepMathIngestionStatus(
            idle = false,
            running = true,
            startedAt = startedAt,
            lastUpdatedAt = startedAt,
            dataset = dataset,
            config = config,
            split = split,
            batchSize = effectiveBatchSize,
            maxRows = effectiveMaxRows,
            requestDelayMs = requestDelayMs.coerceAtLeast(0),
            indexEmbeddings = indexEmbeddings,
            indexChunkSize = indexChunkSize.coerceAtLeast(1),
            indexDelayMs = indexDelayMs.coerceAtLeast(0),
            tokenPresent = !huggingFaceToken.isNullOrBlank(),
            lastError = null
        )

        Thread(
            {
                try {
                    ingestInternal(
                        dataset = dataset,
                        config = config,
                        split = split,
                        batchSize = effectiveBatchSize,
                        maxRows = effectiveMaxRows,
                        requestDelayMs = requestDelayMs.coerceAtLeast(0),
                        indexEmbeddings = indexEmbeddings,
                        indexChunkSize = indexChunkSize.coerceAtLeast(1),
                        indexDelayMs = indexDelayMs.coerceAtLeast(0)
                    )
                } catch (e: Exception) {
                    logger.error("DeepMath ingestion job failed: ${e.message}", e)
                    status = status.copy(
                        running = false,
                        lastUpdatedAt = Instant.now(),
                        lastError = e.message ?: "Unknown error"
                    )
                } finally {
                    running.set(false)
                    status = status.copy(running = false, lastUpdatedAt = Instant.now())
                }
            },
            "deepmath-ingestion-job"
        ).start()

        return StartResult(started = true)
    }

    private fun ingestInternal(
        dataset: String,
        config: String,
        split: String,
        batchSize: Int,
        maxRows: Int,
        requestDelayMs: Long,
        indexEmbeddings: Boolean,
        indexChunkSize: Int,
        indexDelayMs: Long
    ) {
        val prefix = "hf:$dataset:$split:"
        val existing = problemRepository.countBySourceIdStartingWith(prefix)
        if (existing > 0) {
            logger.info("DeepMath ingestion: found $existing existing rows (prefix=$prefix). Will continue ingesting and rely on source_id de-duplication.")
        } else {
            logger.info("DeepMath ingestion: starting fresh (dataset=$dataset, config=$config, split=$split)")
        }

        val totalRows = fetchTotalRows(dataset, config, split)
        val effectiveTotal = when {
            maxRows > 0 -> minOf(maxRows, totalRows)
            else -> totalRows
        }

        status = status.copy(totalRows = totalRows, effectiveTotal = effectiveTotal, lastUpdatedAt = Instant.now())

        logger.info(
            "DeepMath ingestion: totalRows=$totalRows, ingesting=$effectiveTotal, batchSize=$batchSize, " +
                "indexEmbeddings=$indexEmbeddings, requestDelayMs=$requestDelayMs, indexChunkSize=$indexChunkSize, indexDelayMs=$indexDelayMs"
        )

        var offset = 0
        var totalImported = 0
        var totalSkipped = 0
        var totalFailed = 0

        while (offset < effectiveTotal) {
            val length = minOf(batchSize, effectiveTotal - offset)
            val imports = fetchRows(dataset, config, split, offset, length)
                .mapNotNull { parseRowToImport(it, dataset, split) }

            val result = mathDatasetIngestionService.importProblems(
                imports,
                indexAfterSave = indexEmbeddings,
                indexChunkSize = indexChunkSize,
                indexDelayMs = indexDelayMs
            )

            totalImported += result.imported
            totalSkipped += result.skipped
            totalFailed += result.failed

            offset += length

            status = status.copy(
                processedRows = offset,
                imported = totalImported,
                skipped = totalSkipped,
                failed = totalFailed,
                lastUpdatedAt = Instant.now()
            )

            if (offset % (batchSize * 10) == 0 || offset >= effectiveTotal) {
                logger.info("DeepMath ingestion progress: $offset/$effectiveTotal rows processed (imported=$totalImported, skipped=$totalSkipped, failed=$totalFailed)")
            }

            if (requestDelayMs > 0) {
                Thread.sleep(requestDelayMs)
            }
        }

        logger.info("DeepMath ingestion complete: imported=$totalImported, skipped=$totalSkipped, failed=$totalFailed")
    }

    private fun fetchTotalRows(dataset: String, config: String, split: String): Int {
        val json = getJsonWithRetry(
            path = "/size",
            query = mapOf(
                "dataset" to dataset,
                "config" to config,
                "split" to split
            )
        )

        val numRows = json
            .path("size")
            .path("splits")
            .firstOrNull()
            ?.path("num_rows")
            ?.asInt()

        require(numRows != null && numRows > 0) {
            "Unable to determine dataset size from /size endpoint (dataset=$dataset config=$config split=$split)"
        }

        return numRows
    }

    private fun fetchRows(dataset: String, config: String, split: String, offset: Int, length: Int): List<JsonNode> {
        val json = getJsonWithRetry(
            path = "/rows",
            query = mapOf(
                "dataset" to dataset,
                "config" to config,
                "split" to split,
                "offset" to offset.toString(),
                "length" to length.toString()
            )
        )

        val rowsNode = json.path("rows")
        if (!rowsNode.isArray) return emptyList()

        return rowsNode.toList()
    }

    private fun parseRowToImport(rowNode: JsonNode, dataset: String, split: String): MathProblemImport? {
        val rowIdx = rowNode.path("row_idx").asInt(-1)
        val row = rowNode.path("row")
        if (rowIdx < 0 || row.isMissingNode) return null

        val question = row.path("question").asText(null) ?: return null
        val finalAnswer = row.path("final_answer").asText(null)
        val topicPath = row.path("topic").asText(null)

        val r1 = row.path("r1_solution_1").asText(null)
        val r2 = row.path("r1_solution_2").asText(null)
        val r3 = row.path("r1_solution_3").asText(null)

        val difficultyValue = row.path("difficulty").takeIf { !it.isMissingNode && !it.isNull }?.asDouble()
        val difficultyInt = difficultyValue?.roundToInt()

        val (topic, subtopic) = splitTopic(topicPath)

        val solution = buildString {
            finalAnswer?.takeIf { it.isNotBlank() }?.let { append("Final answer: ").append(it.trim()) }

            fun appendRationale(label: String, text: String?) {
                val cleaned = text?.trim()?.takeIf { it.isNotBlank() } ?: return
                if (isNotEmpty()) append("\n\n")
                append(label).append(":\n").append(cleaned)
            }

            appendRationale("Rationale 1", r1)
            appendRationale("Rationale 2", r2)
            appendRationale("Rationale 3", r3)
        }.takeIf { it.isNotBlank() }

        return MathProblemImport(
            sourceId = "hf:$dataset:$split:$rowIdx",
            statement = question,
            solution = solution,
            topic = topic,
            subtopic = subtopic,
            difficulty = difficultyInt
        )
    }

    private fun splitTopic(topicPath: String?): Pair<String, String?> {
        val raw = topicPath?.trim().orEmpty()
        if (raw.isBlank()) return "DeepMath" to null

        val parts = raw.split("->").map { it.trim() }.filter { it.isNotBlank() }
        if (parts.isEmpty()) return "DeepMath" to null

        val full = parts.joinToString(" -> ")
        return if (full.length <= 255) {
            full to null
        } else {
            val head = parts.first().take(255)
            val tail = parts.drop(1).joinToString(" -> ").take(255).takeIf { it.isNotBlank() }
            head to tail
        }
    }

    private fun getJsonWithRetry(path: String, query: Map<String, String>, attempts: Int = 3): JsonNode {
        var lastError: Exception? = null

        repeat(attempts) { attempt ->
            try {
                val request = restClient.get().uri { builder ->
                    builder.path(path)
                    query.forEach { (k, v) -> builder.queryParam(k, v) }
                    builder.build()
                }

                val requestWithAuth = if (!huggingFaceToken.isNullOrBlank()) {
                    request.header(HttpHeaders.AUTHORIZATION, "Bearer ${huggingFaceToken.trim()}")
                } else {
                    request
                }

                val body = requestWithAuth.retrieve().body(String::class.java)
                return objectMapper.readTree(body)
            } catch (e: Exception) {
                lastError = e
                val sleepMs = 500L * (attempt + 1)
                logger.warn("DeepMath ingestion: request failed (attempt ${attempt + 1}/$attempts) for $path: ${e.message}. Retrying in ${sleepMs}ms")
                Thread.sleep(sleepMs)
            }
        }

        throw IllegalStateException("DeepMath ingestion: failed to call $path after $attempts attempts", lastError)
    }
}
