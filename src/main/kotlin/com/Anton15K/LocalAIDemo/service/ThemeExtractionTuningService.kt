package com.Anton15K.LocalAIDemo.service

import kotlin.math.ceil
import kotlin.math.roundToInt
import org.springframework.stereotype.Service

/**
 * Runtime tuning for theme extraction parameters.
 *
 * Goal: derive chunk sizes + aggregation thresholds from transcript size and a user-selected
 * granularity slider (1..10, coarse -> fine).
 */
@Service
class ThemeExtractionTuningService {

    data class TuningRequest(
        /** 1..10 where 1 is coarse (fewer, larger chunks) and 10 is fine (more, smaller chunks). */
        val granularityLevel: Int? = null,
        /** Optional user-provided lecture length in minutes. */
        val lectureMinutes: Int? = null
    )

    data class TunedThemeExtractionSettings(
        val chunkLevelEnabled: Boolean,
        val chunkSizeWords: Int,
        val maxThemesPerChunk: Int,
        val maxFinalThemes: Int,
        val minChunkOccurrences: Int,
        val minOccurrenceRatio: Double
    )

    fun tune(transcript: String, request: TuningRequest?): TunedThemeExtractionSettings {
        val totalWords = countWords(transcript)

        val level = (request?.granularityLevel ?: 5).coerceIn(1, 10)
        val minutes = (request?.lectureMinutes?.takeIf { it > 0 } ?: estimateMinutes(totalWords))
            .coerceAtLeast(5)

        // Length-based cap: roughly +1 theme per ~25 minutes.
        // Examples:
        // 60 min  -> round(60/25)=2  => 3 themes
        // 180 min -> round(180/25)=7 => 8 themes
        val lengthBasedMaxFinalThemes = (minutes / 25.0).roundToInt().plus(1).coerceIn(2, 12)
        val sliderAdjustment = when {
            level <= 3 -> -1
            level >= 8 -> +1
            else -> 0
        }
        val maxFinalThemes = (lengthBasedMaxFinalThemes + sliderAdjustment).coerceIn(2, 12)

        // If transcript is very short, chunking adds overhead and harms recall.
        if (totalWords < 1200) {
            return TunedThemeExtractionSettings(
                chunkLevelEnabled = false,
                chunkSizeWords = 2000,
                maxThemesPerChunk = 3,
                maxFinalThemes = maxFinalThemes.coerceAtMost(6),
                minChunkOccurrences = 2,
                minOccurrenceRatio = 0.20
            )
        }

        // Slider -> target chunk duration in minutes.
        // level=1  => ~15 min chunks
        // level=10 => ~4 min chunks
        val targetChunkMinutes = lerp(15.0, 4.0, (level - 1) / 9.0)

        // Convert to a target number of chunks, clamped to keep work bounded.
        val targetChunks = (minutes / targetChunkMinutes).roundToInt().coerceIn(3, 30)

        // Compute chunk size in words.
        val rawChunkSize = (totalWords.toDouble() / targetChunks.toDouble()).roundToInt()
        val chunkSizeWords = rawChunkSize.coerceIn(600, 2500)

        // Tune aggregation / output size with slider.
        // Make filtering stricter overall (fewer final themes), while still allowing fine granularity.
        val minOccurrenceRatio = lerp(0.26, 0.14, (level - 1) / 9.0).coerceIn(0.10, 0.35)
        // Avoid letting niche one-off themes through too easily.
        val minChunkOccurrences = lerp(4.0, 2.0, (level - 1) / 9.0).roundToInt().coerceIn(2, 6)

        // Also keep per-chunk theme list smaller to reduce noise.
        val maxThemesPerChunk = lerp(2.0, 4.0, (level - 1) / 9.0).roundToInt().coerceIn(2, 6)

        return TunedThemeExtractionSettings(
            chunkLevelEnabled = true,
            chunkSizeWords = chunkSizeWords,
            maxThemesPerChunk = maxThemesPerChunk,
            maxFinalThemes = maxFinalThemes,
            minChunkOccurrences = minChunkOccurrences,
            minOccurrenceRatio = minOccurrenceRatio
        )
    }

    fun computeDynamicMinOccurrences(
        totalChunks: Int,
        settings: TunedThemeExtractionSettings
    ): Int {
        if (totalChunks <= 0) return settings.minChunkOccurrences
        val ratioBased = ceil(totalChunks.toDouble() * settings.minOccurrenceRatio).toInt()
        return maxOf(settings.minChunkOccurrences, ratioBased)
    }

    private fun estimateMinutes(totalWords: Int): Int {
        // Typical lecture speech is often ~140-170 wpm. Use a conservative middle.
        val wordsPerMinute = 150
        return ceil(totalWords.toDouble() / wordsPerMinute.toDouble()).toInt()
    }

    private fun countWords(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        return trimmed.split(Regex("\\s+")).size
    }

    private fun lerp(a: Double, b: Double, t: Double): Double {
        val clampedT = t.coerceIn(0.0, 1.0)
        return a + (b - a) * clampedT
    }
}
