package com.Anton15K.LocalAIDemo.service

import kotlin.math.max
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Aggregates themes extracted from individual chunks into lecture-level themes. Filters out themes
 * that don't meet minimum occurrence thresholds.
 */
@Service
class ChunkThemeAggregationService(
        @Value("\${app.theme-extraction.min-chunk-occurrences:3}")
        private val minChunkOccurrences: Int,
        @Value("\${app.theme-extraction.min-occurrence-ratio:0.10}")
        private val minOccurrenceRatio: Double,
        @Value("\${app.theme-extraction.max-final-themes:12}") private val maxFinalThemes: Int
) {
    private val logger = LoggerFactory.getLogger(ChunkThemeAggregationService::class.java)

    /**
     * Aggregates themes from multiple chunks into deduplicated lecture-level themes.
     *
     * @param chunkThemes List of theme lists, one per chunk
     * @return Aggregated themes meeting minimum occurrence requirements
     */
    fun aggregateThemes(chunkThemes: List<List<ExtractedTheme>>): List<ExtractedTheme> {
        if (chunkThemes.isEmpty()) return emptyList()

        val totalChunks = chunkThemes.size
        val dynamicMinOccurrences =
                max(minChunkOccurrences, (totalChunks * minOccurrenceRatio).toInt())

        logger.debug(
                "Aggregating themes from {} chunks, minOccurrences={}",
                totalChunks,
                dynamicMinOccurrences
        )

        // Group themes by normalized name
        val themeGroups = mutableMapOf<String, MutableList<ExtractedTheme>>()

        for (chunkThemeList in chunkThemes) {
            // Track which normalized names we've seen in THIS chunk to avoid double-counting
            val seenInChunk = mutableSetOf<String>()

            for (theme in chunkThemeList) {
                val normalizedName = normalizeName(theme.name)
                if (normalizedName.isBlank()) continue

                // Only count one occurrence per chunk
                if (normalizedName !in seenInChunk) {
                    seenInChunk.add(normalizedName)
                    themeGroups.computeIfAbsent(normalizedName) { mutableListOf() }.add(theme)
                }
            }
        }

        // Filter by minimum occurrences and aggregate
        val aggregatedThemes =
                themeGroups
                        .filter { (name, occurrences) ->
                            val passes = occurrences.size >= dynamicMinOccurrences
                            if (!passes) {
                                logger.debug(
                                        "Filtered out theme '{}' with only {} occurrences",
                                        name,
                                        occurrences.size
                                )
                            }
                            passes
                        }
                        .map { (_, occurrences) -> mergeThemeOccurrences(occurrences) }
                        .sortedByDescending { it.confidence }
                        .take(maxFinalThemes)

        logger.info(
                "Aggregated {} chunk theme groups into {} final themes",
                themeGroups.size,
                aggregatedThemes.size
        )

        return aggregatedThemes
    }

    private fun normalizeName(name: String): String {
        return name.lowercase().trim()
    }

    private fun mergeThemeOccurrences(occurrences: List<ExtractedTheme>): ExtractedTheme {
        // Use the most common name variant (preserve original casing from most frequent)
        val bestName =
                occurrences.groupingBy { it.name }.eachCount().maxByOrNull { it.value }?.key
                        ?: occurrences.first().name

        // Average confidence
        val avgConfidence = occurrences.map { it.confidence }.average()

        // Merge keywords (deduplicate)
        val mergedKeywords =
                occurrences.flatMap { it.keywords }.map { it.lowercase() }.distinct().take(10)

        // Take the longest summary as the most descriptive
        val bestSummary = occurrences.map { it.summary }.maxByOrNull { it.length } ?: ""

        // Use the most common mapped topic
        val mappedTopic =
                occurrences
                        .mapNotNull { it.mappedTopic }
                        .groupingBy { it }
                        .eachCount()
                        .maxByOrNull { it.value }
                        ?.key

        return ExtractedTheme(
                name = bestName,
                confidence = avgConfidence,
                summary = bestSummary,
                keywords = mergedKeywords,
                mappedTopic = mappedTopic
        )
    }
}
