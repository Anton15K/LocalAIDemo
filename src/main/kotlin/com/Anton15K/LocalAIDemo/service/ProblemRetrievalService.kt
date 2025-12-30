package com.Anton15K.LocalAIDemo.service

import com.Anton15K.LocalAIDemo.domain.Problem
import com.Anton15K.LocalAIDemo.repository.ProblemRepository
import org.slf4j.LoggerFactory
import org.springframework.ai.document.Document
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Result of a problem search with similarity score.
 */
data class ProblemSearchResult(
    val problem: Problem,
    val score: Double,
    val matchedTheme: String?
)

/**
 * Service for retrieving problems based on themes and semantic similarity.
 */
@Service
class ProblemRetrievalService(
    private val problemRepository: ProblemRepository,
    private val embeddingService: EmbeddingService
) {
    private val logger = LoggerFactory.getLogger(ProblemRetrievalService::class.java)

    companion object {
        const val PROBLEM_ID_PREFIX = "problem:"
    }

    /**
     * Find problems by exact topic match.
     */
    fun findByTopic(topic: String, pageable: Pageable): Page<Problem> {
        return problemRepository.findByTopic(topic, pageable)
    }

    /**
     * Find problems by multiple topics.
     */
    fun findByTopics(topics: List<String>, pageable: Pageable): Page<Problem> {
        if (topics.isEmpty()) return Page.empty(pageable)
        return problemRepository.findByTopicIn(topics, pageable)
    }

    /**
     * Search problems using semantic similarity on the extracted themes.
     */
    fun searchByThemes(
        themes: List<ExtractedTheme>,
        topK: Int = 20,
        pageable: Pageable
    ): Page<ProblemSearchResult> {
        if (themes.isEmpty()) return Page.empty(pageable)

        val allowedTopics = themes.mapNotNull { it.mappedTopic }.toSet()

        val allResults = mutableListOf<ProblemSearchResult>()
        val seenProblemIds = mutableSetOf<UUID>()

        for (theme in themes) {
            // Build search query from theme
            val searchQuery = buildSearchQuery(theme)
            
            // Perform vector similarity search
            val documents = embeddingService.similaritySearch(searchQuery, topK)
            
            for (doc in documents) {
                val problemId = extractProblemId(doc.id) ?: continue
                
                if (problemId in seenProblemIds) continue
                seenProblemIds.add(problemId)

                val problem = problemRepository.findById(problemId).orElse(null) ?: continue

                if (allowedTopics.isNotEmpty()) {
                    val requiredTopic = theme.mappedTopic
                    if (requiredTopic != null) {
                        if (problem.topic != requiredTopic) continue
                    } else {
                        if (problem.topic !in allowedTopics) continue
                    }
                }
                
                // Use document metadata score if available, or default
                val score = doc.metadata["score"] as? Double ?: 0.5
                
                allResults.add(ProblemSearchResult(
                    problem = problem,
                    score = clampScore(score * theme.confidence), // Weight by theme confidence
                    matchedTheme = theme.name
                ))
            }
        }

        // Sort by score descending
        val sortedResults = allResults.sortedByDescending { it.score }
        
        // Apply pagination
        val start = pageable.offset.toInt()
        val end = minOf(start + pageable.pageSize, sortedResults.size)
        val pageContent = if (start < sortedResults.size) {
            sortedResults.subList(start, end)
        } else {
            emptyList()
        }

        return PageImpl(pageContent, pageable, sortedResults.size.toLong())
    }

    /**
     * Hybrid search: combine semantic and topic-based retrieval.
     */
    fun hybridSearch(
        themes: List<ExtractedTheme>,
        topK: Int = 20,
        pageable: Pageable
    ): Page<ProblemSearchResult> {
        val results = mutableMapOf<UUID, ProblemSearchResult>()

        val allowedTopics = themes.mapNotNull { it.mappedTopic }.toSet()

        // 1. Topic-based retrieval (exact match on mapped topics)
        val mappedTopics = themes.mapNotNull { it.mappedTopic }.distinct()
        if (mappedTopics.isNotEmpty()) {
            val topicProblems = problemRepository.findByTopicIn(mappedTopics, Pageable.unpaged())
            for (problem in topicProblems) {
                problem.id?.let { id ->
                    val matchedTheme = themes.find { it.mappedTopic == problem.topic }?.name
                    results[id] = ProblemSearchResult(
                        problem = problem,
                        score = clampScore(0.8), // Base score for exact topic match
                        matchedTheme = matchedTheme
                    )
                }
            }
        }

        // 2. Semantic search (vector similarity)
        for (theme in themes) {
            val searchQuery = buildSearchQuery(theme)
            val documents = embeddingService.similaritySearch(searchQuery, topK)
            
            for (doc in documents) {
                val problemId = extractProblemId(doc.id) ?: continue
                val problem = problemRepository.findById(problemId).orElse(null) ?: continue

                if (allowedTopics.isNotEmpty()) {
                    val requiredTopic = theme.mappedTopic
                    if (requiredTopic != null) {
                        if (problem.topic != requiredTopic) continue
                    } else {
                        if (problem.topic !in allowedTopics) continue
                    }
                }
                
                val semanticScore = doc.metadata["score"] as? Double ?: 0.5
                val existingResult = results[problemId]
                
                if (existingResult != null) {
                    // Boost score if found by both methods
                    results[problemId] = existingResult.copy(
                        score = existingResult.score + (semanticScore * 0.5)
                    )
                } else {
                    problem.id?.let { id ->
                        results[id] = ProblemSearchResult(
                            problem = problem,
                            score = clampScore(semanticScore * theme.confidence),
                            matchedTheme = theme.name
                        )
                    }
                }
            }
        }

        // Sort and paginate
        val sortedResults = results.values.sortedByDescending { it.score }
        val start = pageable.offset.toInt()
        val end = minOf(start + pageable.pageSize, sortedResults.size)
        val pageContent = if (start < sortedResults.size) {
            sortedResults.subList(start, end)
        } else {
            emptyList()
        }

        return PageImpl(pageContent, pageable, sortedResults.size.toLong())
    }

    /**
     * Index a problem for vector search.
     */
    fun indexProblem(problem: Problem) {
        val id = problem.id ?: return
        val content = buildProblemContent(problem)
        val metadata = mapOf(
            "sourceId" to problem.sourceId,
            "topic" to problem.topic,
            "difficulty" to (problem.difficulty ?: 0),
            "type" to "problem"
        )

        // IMPORTANT: Spring AI PgVectorStore defaults to UUID ids.
        // Do not prefix, otherwise it will fail to parse as UUID.
        embeddingService.storeDocument(id.toString(), content, metadata)
        logger.debug("Indexed problem: $id")
    }

    /**
     * Index multiple problems in batch.
     */
    fun indexProblems(problems: List<Problem>) {
        val documents = problems.mapNotNull { problem ->
            val id = problem.id ?: return@mapNotNull null
            val content = buildProblemContent(problem)
            Document(
                // Keep ID as UUID string for PgVectorStore compatibility.
                id.toString(),
                content,
                mapOf(
                    "sourceId" to problem.sourceId,
                    "topic" to problem.topic,
                    "difficulty" to (problem.difficulty ?: 0),
                    "type" to "problem"
                )
            )
        }
        
        if (documents.isNotEmpty()) {
            embeddingService.storeDocuments(documents)
            logger.info("Indexed ${documents.size} problems")
        }
    }

    private fun buildSearchQuery(theme: ExtractedTheme): String {
        val parts = mutableListOf<String>()

        // When available, anchor the semantic query to the mapped topic so
        // generic terms like "vector" don't drift across domains.
        val mappedTopic = theme.mappedTopic
        if (!mappedTopic.isNullOrBlank()) {
            parts.add("Topic: $mappedTopic")
        }

        parts.add(theme.name)
        if (theme.keywords.isNotEmpty()) {
            parts.add(theme.keywords.joinToString(" "))
        }
        theme.summary.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        return parts.joinToString(". ")
    }

    private fun buildProblemContent(problem: Problem): String {
        val parts = mutableListOf<String>()
        parts.add("Topic: ${problem.topic}")
        problem.subtopic?.let { parts.add("Subtopic: $it") }
        parts.add("Problem: ${problem.statement}")
        return parts.joinToString("\n")
    }

    private fun extractProblemId(documentId: String?): UUID? {
        if (documentId.isNullOrBlank()) return null
        val raw = if (documentId.startsWith(PROBLEM_ID_PREFIX)) {
            documentId.removePrefix(PROBLEM_ID_PREFIX)
        } else {
            documentId
        }

        return try {
            UUID.fromString(raw)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun clampScore(score: Double): Double {
        // This score is a ranking signal, not a calibrated probability.
        // Clamp to avoid UI showing >100% when we combine signals.
        return score.coerceIn(0.0, 1.0)
    }
}
