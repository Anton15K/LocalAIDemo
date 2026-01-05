package com.Anton15K.LocalAIDemo.service

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChunkThemeAggregationServiceTest {

    @Test
    fun aggregateThemes_filtersByOccurrences_andMergesFields() {
        val service = ChunkThemeAggregationService(
            minChunkOccurrences = 2,
            minOccurrenceRatio = 0.0,
            maxFinalThemes = 12
        )

        val chunkThemes = listOf(
            listOf(
                ExtractedTheme(
                    name = "Algebra",
                    confidence = 0.9,
                    summary = "short",
                    keywords = listOf("X", "Y"),
                    mappedTopic = "Algebra"
                ),
                ExtractedTheme(
                    name = "Calculus",
                    confidence = 0.8,
                    summary = "only appears once",
                    keywords = listOf("Derivative"),
                    mappedTopic = "Calculus"
                )
            ),
            listOf(
                ExtractedTheme(
                    name = "algebra",
                    confidence = 0.7,
                    summary = "a much longer summary",
                    keywords = listOf("y", "Z"),
                    mappedTopic = "Algebra"
                ),
                // Same chunk duplicate should not increase occurrence count
                ExtractedTheme(
                    name = " Algebra ",
                    confidence = 0.2,
                    summary = "duplicate in same chunk",
                    keywords = listOf("ignored"),
                    mappedTopic = "Algebra"
                )
            ),
            listOf(
                ExtractedTheme(
                    name = "ALGEBRA",
                    confidence = 0.8,
                    summary = "mid",
                    keywords = listOf("x"),
                    mappedTopic = "Linear Algebra"
                )
            )
        )

        val aggregated = service.aggregateThemes(chunkThemes)

        assertEquals(1, aggregated.size)
        val algebra = aggregated.first()

        assertTrue(algebra.name in setOf("Algebra", "algebra", "ALGEBRA", " Algebra "))
        assertTrue(abs(algebra.confidence - 0.8) < 1e-9)
        assertEquals("a much longer summary", algebra.summary)
        assertEquals(listOf("x", "y", "z"), algebra.keywords)
        assertNotNull(algebra.mappedTopic)
        assertEquals("Algebra", algebra.mappedTopic)
    }

    @Test
    fun aggregateThemes_respectsMaxFinalThemesOverride_andSortsByConfidence() {
        val service = ChunkThemeAggregationService(
            minChunkOccurrences = 2,
            minOccurrenceRatio = 0.0,
            maxFinalThemes = 12
        )

        val chunkThemes = listOf(
            listOf(
                ExtractedTheme("A", 1.0, "", emptyList(), null),
                ExtractedTheme("B", 0.6, "", emptyList(), null)
            ),
            listOf(
                ExtractedTheme("A", 0.8, "", emptyList(), null),
                ExtractedTheme("B", 0.7, "", emptyList(), null)
            )
        )

        val aggregated = service.aggregateThemes(chunkThemes, maxFinalThemesOverride = 1)

        assertEquals(1, aggregated.size)
        assertEquals("A", aggregated.first().name)
        assertTrue(aggregated.first().confidence > 0.8)
    }
}
