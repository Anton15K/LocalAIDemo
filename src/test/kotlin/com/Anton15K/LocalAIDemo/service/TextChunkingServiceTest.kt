package com.Anton15K.LocalAIDemo.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextChunkingServiceTest {
    private val service = TextChunkingService()

    @Test
    fun chunkText_blank_returnsEmpty() {
        assertEquals(emptyList(), service.chunkText("   "))
    }

    @Test
    fun chunkText_createsOverlappingChunks_andSetsTokenRanges() {
        val text = (1..10).joinToString(" ") { "w$it" }

        val chunks = service.chunkText(text, chunkSize = 4, overlap = 1)

        assertEquals(3, chunks.size)

        assertEquals(0, chunks[0].tokenStart)
        assertEquals(3, chunks[0].tokenEnd)
        assertEquals("w1 w2 w3 w4", chunks[0].text)

        assertEquals(3, chunks[1].tokenStart)
        assertEquals(6, chunks[1].tokenEnd)
        assertEquals("w4 w5 w6 w7", chunks[1].text)

        assertEquals(6, chunks[2].tokenStart)
        assertEquals(9, chunks[2].tokenEnd)
        assertEquals("w7 w8 w9 w10", chunks[2].text)
    }

    @Test
    fun chunkBySentences_splitsIntoMultipleChunks_whenTargetSizeExceeded() {
        val text = "One two three. Four five six seven. Eight nine."

        val chunks = service.chunkBySentences(text, targetChunkSize = 5)

        assertEquals(3, chunks.size)

        assertEquals("One two three.", chunks[0].text)
        assertEquals(0, chunks[0].tokenStart)
        assertEquals(2, chunks[0].tokenEnd)

        assertEquals("Four five six seven.", chunks[1].text)
        assertEquals(3, chunks[1].tokenStart)
        assertEquals(6, chunks[1].tokenEnd)

        assertEquals("Eight nine.", chunks[2].text)
        assertEquals(7, chunks[2].tokenStart)
        assertEquals(8, chunks[2].tokenEnd)
    }

    @Test
    fun chunkSemantically_splitsHugeParagraphBySentences_andUsesAbsoluteTokenOffsets() {
        val paragraph = "A1 A2 A3 A4 A5. B1 B2 B3 B4 B5. C1 C2." // 12 words
        val chunks = service.chunkSemantically(paragraph, targetChunkSize = 5)

        assertEquals(3, chunks.size)
        assertTrue(chunks.map { it.index } == listOf(0, 1, 2))

        // First 5-word sentence chunk
        assertEquals(0, chunks[0].tokenStart)
        assertEquals(4, chunks[0].tokenEnd)

        // Second 5-word sentence chunk
        assertEquals(5, chunks[1].tokenStart)
        assertEquals(9, chunks[1].tokenEnd)

        // Last 2-word chunk
        assertEquals(10, chunks[2].tokenStart)
        assertEquals(11, chunks[2].tokenEnd)
    }
}
