package com.Anton15K.LocalAIDemo.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeExtractionTuningServiceTest {
    private val service = ThemeExtractionTuningService()

    @Test
    fun tune_shortTranscript_disablesChunkLevelExtraction() {
        val transcript = (1..1000).joinToString(" ") { "w$it" }

        val settings = service.tune(transcript, request = null)

        assertEquals(false, settings.chunkLevelEnabled)
        assertEquals(2000, settings.chunkSizeWords)
        assertEquals(3, settings.maxThemesPerChunk)
        assertTrue(settings.maxFinalThemes in 2..6)
        assertEquals(2, settings.minChunkOccurrences)
        assertEquals(0.20, settings.minOccurrenceRatio)
    }

    @Test
    fun tune_longTranscript_respectsGranularitySlider() {
        val transcript = (1..6000).joinToString(" ") { "w$it" }

        val coarse = service.tune(transcript, ThemeExtractionTuningService.TuningRequest(granularityLevel = 1))
        val fine = service.tune(transcript, ThemeExtractionTuningService.TuningRequest(granularityLevel = 10))

        assertEquals(true, coarse.chunkLevelEnabled)
        assertEquals(true, fine.chunkLevelEnabled)

        // Fine granularity => more chunks => smaller per-chunk size.
        assertTrue(fine.chunkSizeWords < coarse.chunkSizeWords)

        // Fine granularity => allow slightly more themes per chunk, and be less strict on ratio/occurrences.
        assertTrue(fine.maxThemesPerChunk >= coarse.maxThemesPerChunk)
        assertTrue(fine.minOccurrenceRatio <= coarse.minOccurrenceRatio)
        assertTrue(fine.minChunkOccurrences <= coarse.minChunkOccurrences)

        // Safety bounds
        assertTrue(coarse.chunkSizeWords in 600..2500)
        assertTrue(fine.chunkSizeWords in 600..2500)
        assertTrue(coarse.maxThemesPerChunk in 2..6)
        assertTrue(fine.maxThemesPerChunk in 2..6)
    }

    @Test
    fun computeDynamicMinOccurrences_usesMaxOfAbsoluteAndRatioBased() {
        val settings = ThemeExtractionTuningService.TunedThemeExtractionSettings(
            chunkLevelEnabled = true,
            chunkSizeWords = 1000,
            maxThemesPerChunk = 3,
            maxFinalThemes = 8,
            minChunkOccurrences = 3,
            minOccurrenceRatio = 0.2
        )

        // ceil(10 * 0.2) = 2, but absolute min is 3
        assertEquals(3, service.computeDynamicMinOccurrences(totalChunks = 10, settings = settings))

        val settingsRatioDominates = settings.copy(minOccurrenceRatio = 0.6)
        // ceil(10 * 0.6) = 6
        assertEquals(6, service.computeDynamicMinOccurrences(totalChunks = 10, settings = settingsRatioDominates))
    }
}
