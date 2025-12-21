package com.Anton15K.LocalAIDemo.service

import com.Anton15K.LocalAIDemo.domain.Problem
import com.Anton15K.LocalAIDemo.repository.ProblemRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream
import java.time.Instant

/**
 * Data class for importing problems from MATH dataset.
 */
data class MathProblemImport(
    val sourceId: String,
    val statement: String,
    val solution: String?,
    val topic: String,
    val subtopic: String? = null,
    val difficulty: Int? = null
)

/**
 * Service for ingesting problems from the MATH dataset.
 */
@Service
class MathDatasetIngestionService(
    private val problemRepository: ProblemRepository,
    private val problemRetrievalService: ProblemRetrievalService,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(MathDatasetIngestionService::class.java)

    /**
     * Import a single problem.
     */
    @Transactional
    fun importProblem(import: MathProblemImport): Problem {
        // Check if already exists
        val existing = problemRepository.findBySourceId(import.sourceId)
        if (existing != null) {
            logger.debug("Problem already exists: ${import.sourceId}")
            return existing
        }

        val problem = Problem(
            sourceId = import.sourceId,
            statement = import.statement,
            solution = import.solution,
            topic = import.topic,
            subtopic = import.subtopic,
            difficulty = import.difficulty,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        val saved = problemRepository.save(problem)
        
        // Index for vector search
        problemRetrievalService.indexProblem(saved)
        
        logger.debug("Imported problem: ${saved.sourceId}")
        return saved
    }

    /**
     * Import multiple problems in batch.
     */
    @Transactional
    fun importProblems(imports: List<MathProblemImport>): ImportResult {
        var imported = 0
        var skipped = 0
        var failed = 0
        val problems = mutableListOf<Problem>()

        for (import in imports) {
            try {
                if (problemRepository.existsBySourceId(import.sourceId)) {
                    skipped++
                    continue
                }

                val problem = Problem(
                    sourceId = import.sourceId,
                    statement = import.statement,
                    solution = import.solution,
                    topic = import.topic,
                    subtopic = import.subtopic,
                    difficulty = import.difficulty,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
                problems.add(problem)
                imported++
            } catch (e: Exception) {
                logger.error("Failed to import problem ${import.sourceId}: ${e.message}")
                failed++
            }
        }

        // Batch save
        if (problems.isNotEmpty()) {
            val saved = problemRepository.saveAll(problems)
            
            // Batch index for vector search
            problemRetrievalService.indexProblems(saved)
        }

        logger.info("Import complete: $imported imported, $skipped skipped, $failed failed")
        return ImportResult(imported, skipped, failed)
    }

    /**
     * Import problems from JSONL format (one JSON object per line).
     */
    @Transactional
    fun importFromJsonl(inputStream: InputStream, topic: String): ImportResult {
        val imports = mutableListOf<MathProblemImport>()
        var lineNumber = 0

        inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                lineNumber++
                if (line.isBlank()) return@forEach

                try {
                    val node = objectMapper.readTree(line)
                    val import = parseMathProblem(node, topic, lineNumber)
                    if (import != null) {
                        imports.add(import)
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to parse line $lineNumber: ${e.message}")
                }
            }
        }

        return importProblems(imports)
    }

    /**
     * Import problems from JSON array format.
     */
    @Transactional
    fun importFromJsonArray(inputStream: InputStream, topic: String): ImportResult {
        val node = objectMapper.readTree(inputStream)
        if (!node.isArray) {
            throw IllegalArgumentException("Expected JSON array")
        }

        val imports = mutableListOf<MathProblemImport>()
        node.forEachIndexed { index, problemNode ->
            val import = parseMathProblem(problemNode, topic, index)
            if (import != null) {
                imports.add(import)
            }
        }

        return importProblems(imports)
    }

    /**
     * Create sample problems for testing.
     */
    @Transactional
    fun createSampleProblems(): ImportResult {
        val samples = listOf(
            MathProblemImport(
                sourceId = "sample-algebra-1",
                statement = "Solve for x: 2x + 5 = 13",
                solution = "x = 4",
                topic = "Algebra",
                subtopic = "Linear Equations",
                difficulty = 1
            ),
            MathProblemImport(
                sourceId = "sample-algebra-2",
                statement = "Factor the expression: x² - 9",
                solution = "(x + 3)(x - 3)",
                topic = "Algebra",
                subtopic = "Factoring",
                difficulty = 2
            ),
            MathProblemImport(
                sourceId = "sample-geometry-1",
                statement = "Find the area of a circle with radius 5.",
                solution = "A = πr² = 25π ≈ 78.54",
                topic = "Geometry",
                subtopic = "Circles",
                difficulty = 1
            ),
            MathProblemImport(
                sourceId = "sample-calculus-1",
                statement = "Find the derivative of f(x) = x³ + 2x",
                solution = "f'(x) = 3x² + 2",
                topic = "Calculus",
                subtopic = "Derivatives",
                difficulty = 2
            ),
            MathProblemImport(
                sourceId = "sample-probability-1",
                statement = "What is the probability of rolling a 6 on a fair die?",
                solution = "1/6",
                topic = "Probability",
                subtopic = "Basic Probability",
                difficulty = 1
            ),
            MathProblemImport(
                sourceId = "sample-number-theory-1",
                statement = "Find the GCD of 48 and 18.",
                solution = "GCD(48, 18) = 6",
                topic = "Number Theory",
                subtopic = "GCD",
                difficulty = 1
            ),
            MathProblemImport(
                sourceId = "sample-combinatorics-1",
                statement = "How many ways can you arrange 4 books on a shelf?",
                solution = "4! = 24 ways",
                topic = "Combinatorics",
                subtopic = "Permutations",
                difficulty = 1
            ),
            MathProblemImport(
                sourceId = "sample-trigonometry-1",
                statement = "Find sin(30°).",
                solution = "sin(30°) = 1/2",
                topic = "Trigonometry",
                subtopic = "Basic Trigonometry",
                difficulty = 1
            )
        )

        return importProblems(samples)
    }

    private fun parseMathProblem(node: JsonNode, defaultTopic: String, index: Int): MathProblemImport? {
        val problem = node.get("problem")?.asText() ?: return null
        val solution = node.get("solution")?.asText()
        val level = node.get("level")?.asText()?.let { parseDifficulty(it) }
        val type = node.get("type")?.asText() ?: defaultTopic

        return MathProblemImport(
            sourceId = "$defaultTopic-$index",
            statement = problem,
            solution = solution,
            topic = type,
            difficulty = level
        )
    }

    private fun parseDifficulty(level: String): Int? {
        return when {
            level.contains("1") -> 1
            level.contains("2") -> 2
            level.contains("3") -> 3
            level.contains("4") -> 4
            level.contains("5") -> 5
            else -> null
        }
    }
}

data class ImportResult(
    val imported: Int,
    val skipped: Int,
    val failed: Int
)
