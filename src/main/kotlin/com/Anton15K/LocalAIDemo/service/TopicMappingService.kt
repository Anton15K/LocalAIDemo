package com.Anton15K.LocalAIDemo.service

import com.Anton15K.LocalAIDemo.repository.ProblemRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class TopicMappingService(
    private val problemRepository: ProblemRepository,
    @Value("\${app.topic-mapping.cache-ttl-seconds:600}")
    private val cacheTtlSeconds: Long,
    @Value("\${app.topic-mapping.max-candidates:5000}")
    private val maxCandidates: Int,
    @Value("\${app.topic-mapping.min-score:3}")
    private val minScore: Int
) {
    private val logger = LoggerFactory.getLogger(TopicMappingService::class.java)

    @Volatile
    private var cache: TopicCatalog? = null

    fun mapThemesToExistingTopics(themes: List<ExtractedTheme>): List<ExtractedTheme> {
        if (themes.isEmpty()) return themes

        val catalog = getCatalog()
        if (catalog.topics.isEmpty()) return themes

        return themes.map { theme ->
            val mapped = mapTheme(theme, catalog)
            theme.copy(mappedTopic = mapped)
        }
    }

    private fun mapTheme(theme: ExtractedTheme, catalog: TopicCatalog): String? {
        val queryTokens = tokenize("${theme.name} ${theme.keywords.joinToString(" ")} ${theme.summary}")
        if (queryTokens.isEmpty()) return null

        val geometrySignal = queryTokens.any { it in GEOMETRY_HINTS }
        val linearAlgebraSignal = queryTokens.any { it in LINEAR_ALGEBRA_HINTS }

        val candidateScores = mutableMapOf<String, Int>()

        for (token in queryTokens) {
            val topicsForToken = catalog.tokenToTopics[token] ?: continue
            for (topic in topicsForToken) {
                candidateScores[topic] = (candidateScores[topic] ?: 0) + 2
            }
        }

        // If nothing matched by token index, fall back to a small scan over all topics.
        val initialCandidates = if (candidateScores.isNotEmpty()) {
            candidateScores.entries
                .sortedByDescending { it.value }
                .take(maxCandidates.coerceAtLeast(1))
                .map { it.key }
        } else {
            catalog.topics.take(maxCandidates.coerceAtLeast(1))
        }

        val themeNameLower = theme.name.lowercase()

        var bestTopic: String? = null
        var bestScore = Int.MIN_VALUE

        for (topic in initialCandidates) {
            val topicLower = topic.lowercase()
            var score = candidateScores[topic] ?: 0

            // Domain-specific disambiguation:
            // "vector" alone is ambiguous; we boost Geometry vs Linear Algebra only when
            // the theme text contains stronger contextual cues.
            if (geometrySignal && topicLower.contains("geometry")) score += 6
            if (linearAlgebraSignal && (topicLower.contains("linear algebra") || topicLower.contains("linalg"))) score += 6

            // Some datasets use topic paths like "Geometry -> Vectors".
            if (geometrySignal && topicLower.contains("coordinate")) score += 2
            if (linearAlgebraSignal && topicLower.contains("matrix")) score += 2

            // Strong boost for direct substring matches.
            if (themeNameLower.length >= 4 && topicLower.contains(themeNameLower)) {
                score += 8
            }

            // Extra boosts for matching last segment in hierarchical topics.
            val lastSegment = topicLower.split("->").lastOrNull()?.trim().orEmpty()
            for (token in queryTokens) {
                if (lastSegment == token) score += 3
            }

            if (score > bestScore) {
                bestScore = score
                bestTopic = topic
            }
        }

        return if (bestScore >= minScore) bestTopic else null
    }

    private fun getCatalog(): TopicCatalog {
        val now = Instant.now()
        val existing = cache
        if (existing != null && Duration.between(existing.loadedAt, now).seconds < cacheTtlSeconds) {
            return existing
        }

        synchronized(this) {
            val existing2 = cache
            if (existing2 != null && Duration.between(existing2.loadedAt, now).seconds < cacheTtlSeconds) {
                return existing2
            }

            val topics = problemRepository.findAllTopics()
            val tokenToTopics = mutableMapOf<String, MutableList<String>>()

            for (topic in topics) {
                val tokens = tokenize(topic)
                for (t in tokens) {
                    tokenToTopics.computeIfAbsent(t) { mutableListOf() }.add(topic)
                }
            }

            val built = TopicCatalog(
                topics = topics,
                tokenToTopics = tokenToTopics,
                loadedAt = now
            )
            cache = built
            logger.info("TopicMappingService: cached ${topics.size} topics")
            return built
        }
    }

    private fun tokenize(text: String): Set<String> {
        val raw = text.lowercase()
        val tokens = raw.split(Regex("[^a-z0-9]+"))
            .map { it.trim() }
            .filter { it.length >= 3 || it in SHORT_DOMAIN_TOKENS }
            .filterNot { it in STOPWORDS }

        return tokens.toSet()
    }

    private data class TopicCatalog(
        val topics: List<String>,
        val tokenToTopics: Map<String, List<String>>,
        val loadedAt: Instant
    )

    companion object {
        private val SHORT_DOMAIN_TOKENS = setOf("2d", "3d")

        // These lists are intentionally small and conservative: they're used only to
        // disambiguate closely related topics (Geometry vs Linear Algebra) when the
        // theme text contains clear context.
        private val GEOMETRY_HINTS = setOf(
            "angle", "angles", "triangle", "triangles", "circle", "circles", "sphere", "distance",
            "length", "area", "perimeter", "radius", "diameter", "chord",
            "slope", "midpoint", "line", "lines", "plane", "planes",
            "coordinate", "coordinates", "axis", "axes", "polygon", "polygons",
            "parallel", "perpendicular", "intersection",
            "dot", "cross", "projection", "rotate", "rotation", "reflect", "reflection",
            "norm", "magnitude", "unit", "direction", "2d", "3d"
        )

        private val LINEAR_ALGEBRA_HINTS = setOf(
            "matrix", "matrices", "determinant", "det", "rank",
            "eigen", "eigenvalue", "eigenvalues", "eigenvector", "eigenvectors",
            "basis", "span", "subspace", "nullspace", "kernel", "image",
            "independent", "independence", "dependent", "dependence",
            "diagonal", "invertible", "inverse", "gaussian", "elimination",
            "orthonormal", "orthogonal", "linalg"
        )

        private val STOPWORDS = setOf(
            "the", "and", "for", "with", "from", "this", "that", "into", "over", "under", "then", "than",
            "when", "where", "what", "which", "whose", "your", "you", "are", "was", "were", "have", "has",
            "had", "not", "but", "can", "will", "its", "let", "use", "using", "used", "also", "only"
        )
    }
}
