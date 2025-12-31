package com.Anton15K.LocalAIDemo.service

import com.Anton15K.LocalAIDemo.repository.ProblemRepository
import java.time.Duration
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class TopicMappingService(
        private val problemRepository: ProblemRepository,
        @Value("\${app.topic-mapping.cache-ttl-seconds:600}") private val cacheTtlSeconds: Long,
        @Value("\${app.topic-mapping.max-candidates:5000}") private val maxCandidates: Int,
        @Value("\${app.topic-mapping.min-score:3}") private val minScore: Int
) {
    private val logger = LoggerFactory.getLogger(TopicMappingService::class.java)

    @Volatile private var cache: TopicCatalog? = null

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
        val queryTokens =
                tokenize("${theme.name} ${theme.keywords.joinToString(" ")} ${theme.summary}")
        if (queryTokens.isEmpty()) return null

        val geometrySignal = queryTokens.any { it in GEOMETRY_HINTS }
        val linearAlgebraSignal = queryTokens.any { it in LINEAR_ALGEBRA_HINTS }
        val euclideanSignal = queryTokens.any { it in EUCLIDEAN_HINTS }
        val nonEuclideanSignal = queryTokens.any { it in NON_EUCLIDEAN_HINTS }

        val candidateScores = mutableMapOf<String, Int>()

        for (token in queryTokens) {
            val topicsForToken = catalog.tokenToTopics[token] ?: continue
            for (topic in topicsForToken) {
                candidateScores[topic] = (candidateScores[topic] ?: 0) + 2
            }
        }

        // If nothing matched by token index, fall back to a small scan over all topics.
        val initialCandidates =
                if (candidateScores.isNotEmpty()) {
                    candidateScores
                            .entries
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
            if (linearAlgebraSignal &&
                            (topicLower.contains("linear algebra") || topicLower.contains("linalg"))
            )
                    score += 6

            // Some datasets use topic paths like "Geometry -> Vectors".
            if (geometrySignal && topicLower.contains("coordinate")) score += 2
            if (linearAlgebraSignal && topicLower.contains("matrix")) score += 2

            // Penalize cross-domain mismatches to avoid geometry<->linear algebra confusion
            if (geometrySignal && !linearAlgebraSignal) {
                if (topicLower.contains("linear algebra") ||
                                topicLower.contains("linalg") ||
                                (topicLower.contains("matrix") && !topicLower.contains("geometry"))
                ) {
                    score -= 8 // Strong penalty for LA topics when geometry context is clear
                }
            }
            if (linearAlgebraSignal && !geometrySignal) {
                if (topicLower.contains("geometry") || topicLower.contains("coordinate")) {
                    score -= 8 // Strong penalty for geometry topics when LA context is clear
                }
            }

            // Penalize Euclidean<->non-Euclidean geometry mismatches
            if (euclideanSignal && !nonEuclideanSignal) {
                if (topicLower.contains("non-euclidean") ||
                                topicLower.contains("noneuclidean") ||
                                topicLower.contains("hyperbolic") ||
                                topicLower.contains("spherical") ||
                                topicLower.contains("elliptic") ||
                                topicLower.contains("riemannian")
                ) {
                    score -= 8 // Strong penalty for non-Euclidean when Euclidean context is clear
                }
            }
            if (nonEuclideanSignal && !euclideanSignal) {
                if (topicLower.contains("euclidean") &&
                                !topicLower.contains("non-euclidean") &&
                                !topicLower.contains("noneuclidean")
                ) {
                    score -= 8 // Strong penalty for Euclidean when non-Euclidean context is clear
                }
            }

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
        if (existing != null && Duration.between(existing.loadedAt, now).seconds < cacheTtlSeconds
        ) {
            return existing
        }

        synchronized(this) {
            val existing2 = cache
            if (existing2 != null &&
                            Duration.between(existing2.loadedAt, now).seconds < cacheTtlSeconds
            ) {
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

            val built = TopicCatalog(topics = topics, tokenToTopics = tokenToTopics, loadedAt = now)
            cache = built
            logger.info("TopicMappingService: cached ${topics.size} topics")
            return built
        }
    }

    private fun tokenize(text: String): Set<String> {
        val raw = text.lowercase()
        val tokens =
                raw.split(Regex("[^a-z0-9]+"))
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
        private val GEOMETRY_HINTS =
                setOf(
                        "angle",
                        "angles",
                        "triangle",
                        "triangles",
                        "circle",
                        "circles",
                        "sphere",
                        "distance",
                        "length",
                        "area",
                        "perimeter",
                        "radius",
                        "diameter",
                        "chord",
                        "slope",
                        "midpoint",
                        "line",
                        "lines",
                        "plane",
                        "planes",
                        "coordinate",
                        "coordinates",
                        "axis",
                        "axes",
                        "polygon",
                        "polygons",
                        "parallel",
                        "perpendicular",
                        "intersection",
                        "dot",
                        "cross",
                        "rotate",
                        "rotation",
                        "reflect",
                        "reflection",
                        "norm",
                        "magnitude",
                        "unit",
                        "direction",
                        "2d",
                        "3d",
                        "pythagorean",
                        "congruent",
                        "similar",
                        "tangent",
                        "secant",
                        "vertex",
                        "vertices",
                        "collinear",
                        "coplanar",
                        "centroid",
                        "circumference"
                )

        private val LINEAR_ALGEBRA_HINTS =
                setOf(
                        "matrix",
                        "matrices",
                        "determinant",
                        "det",
                        "rank",
                        "eigen",
                        "eigenvalue",
                        "eigenvalues",
                        "eigenvector",
                        "eigenvectors",
                        "basis",
                        "span",
                        "subspace",
                        "nullspace",
                        "kernel",
                        "image",
                        "independent",
                        "independence",
                        "dependent",
                        "dependence",
                        "diagonal",
                        "invertible",
                        "inverse",
                        "gaussian",
                        "elimination",
                        "orthonormal",
                        "orthogonal",
                        "linalg",
                        "transpose",
                        "trace",
                        "singular",
                        "decomposition",
                        "factorization",
                        "projection",
                        "transformation",
                        "homogeneous",
                        "nonhomogeneous"
                )

        private val STOPWORDS =
                setOf(
                        "the",
                        "and",
                        "for",
                        "with",
                        "from",
                        "this",
                        "that",
                        "into",
                        "over",
                        "under",
                        "then",
                        "than",
                        "when",
                        "where",
                        "what",
                        "which",
                        "whose",
                        "your",
                        "you",
                        "are",
                        "was",
                        "were",
                        "have",
                        "has",
                        "had",
                        "not",
                        "but",
                        "can",
                        "will",
                        "its",
                        "let",
                        "use",
                        "using",
                        "used",
                        "also",
                        "only"
                )

        // Euclidean geometry hints - classical/flat geometry
        private val EUCLIDEAN_HINTS =
                setOf(
                        "euclidean",
                        "cartesian",
                        "pythagorean",
                        "parallel",
                        "perpendicular",
                        "congruent",
                        "similar",
                        "parallelogram",
                        "rectangle",
                        "square",
                        "rhombus",
                        "trapezoid"
                )

        // Non-Euclidean geometry hints - curved/non-flat geometry
        private val NON_EUCLIDEAN_HINTS =
                setOf(
                        "hyperbolic",
                        "spherical",
                        "elliptic",
                        "riemannian",
                        "lobachevsky",
                        "geodesic",
                        "curvature",
                        "manifold",
                        "poincare",
                        "minkowski",
                        "noneuclidean",
                        "non-euclidean"
                )
    }
}
