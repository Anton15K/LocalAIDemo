package com.Anton15K.LocalAIDemo.service

import org.springframework.stereotype.Service

/**
 * Service for chunking text into smaller pieces for embedding.
 */
@Service
class TextChunkingService {

    companion object {
        const val DEFAULT_CHUNK_SIZE = 500
        const val DEFAULT_OVERLAP = 50
    }

    /**
     * Split text into overlapping chunks.
     */
    fun chunkText(
        text: String,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        overlap: Int = DEFAULT_OVERLAP
    ): List<TextChunk> {
        if (text.isBlank()) return emptyList()
        
        val words = text.split(Regex("\\s+"))
        val chunks = mutableListOf<TextChunk>()
        var startIndex = 0
        var chunkIndex = 0
        
        while (startIndex < words.size) {
            val endIndex = minOf(startIndex + chunkSize, words.size)
            val chunkWords = words.subList(startIndex, endIndex)
            val chunkText = chunkWords.joinToString(" ")
            
            chunks.add(TextChunk(
                text = chunkText,
                index = chunkIndex,
                tokenStart = startIndex,
                tokenEnd = endIndex - 1
            ))
            
            chunkIndex++
            startIndex += (chunkSize - overlap)
            
            // Prevent infinite loop for small texts
            if (endIndex >= words.size) break
        }
        
        return chunks
    }

    /**
     * Split text by sentences, grouping into chunks of approximately target size.
     */
    fun chunkBySentences(
        text: String,
        targetChunkSize: Int = DEFAULT_CHUNK_SIZE
    ): List<TextChunk> {
        if (text.isBlank()) return emptyList()

        // Split by sentence boundaries
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        val chunks = mutableListOf<TextChunk>()
        var currentChunk = StringBuilder()
        var chunkIndex = 0
        var tokenStart = 0
        var currentTokenCount = 0

        for (sentence in sentences) {
            val sentenceWordCount = sentence.split(Regex("\\s+")).size
            
            if (currentTokenCount + sentenceWordCount > targetChunkSize && currentChunk.isNotEmpty()) {
                // Save current chunk and start new one
                chunks.add(TextChunk(
                    text = currentChunk.toString().trim(),
                    index = chunkIndex,
                    tokenStart = tokenStart,
                    tokenEnd = tokenStart + currentTokenCount - 1
                ))
                chunkIndex++
                tokenStart += currentTokenCount
                currentChunk = StringBuilder()
                currentTokenCount = 0
            }
            
            if (currentChunk.isNotEmpty()) currentChunk.append(" ")
            currentChunk.append(sentence)
            currentTokenCount += sentenceWordCount
        }

        // Don't forget the last chunk
        if (currentChunk.isNotEmpty()) {
            chunks.add(TextChunk(
                text = currentChunk.toString().trim(),
                index = chunkIndex,
                tokenStart = tokenStart,
                tokenEnd = tokenStart + currentTokenCount - 1
            ))
        }

        return chunks
    }
}

data class TextChunk(
    val text: String,
    val index: Int,
    val tokenStart: Int,
    val tokenEnd: Int
)
