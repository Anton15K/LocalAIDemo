package com.Anton15K.LocalAIDemo.service

import org.springframework.stereotype.Service

/** Service for chunking text into smaller pieces for embedding. */
@Service
class TextChunkingService {
    companion object {
        const val DEFAULT_CHUNK_SIZE = 2000
        const val DEFAULT_OVERLAP = 50
    }

    /** Split text into overlapping chunks. */
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

            chunks.add(
                    TextChunk(
                            text = chunkText,
                            index = chunkIndex,
                            tokenStart = startIndex,
                            tokenEnd = endIndex - 1
                    )
            )

            chunkIndex++
            startIndex += (chunkSize - overlap)

            // Prevent infinite loop for small texts
            if (endIndex >= words.size) break
        }

        return chunks
    }

    /**
     * Split text semantically by paragraphs. Tries to keep paragraphs together. If a paragraph
     * exceeds chunk size, splits by sentences.
     */
    fun chunkSemantically(
            text: String,
            targetChunkSize: Int = DEFAULT_CHUNK_SIZE
    ): List<TextChunk> {
        if (text.isBlank()) return emptyList()

        // 1. Split by double newlines (paragraphs)
        /*
        val paragraphs = text.split(Regex("\\n\\s*\\n"))
         */
        val paragraphs = text.split("\n")
        val chunks = mutableListOf<TextChunk>()

        var currentChunkText = StringBuilder()
        var chunkIndex = 0
        var tokenStart = 0
        var currentChunkWordCount = 0

        for (paragraph in paragraphs) {
            val paragraphTrimmed = paragraph.trim()
            if (paragraphTrimmed.isEmpty()) continue

            val paragraphWordCount = countWords(paragraphTrimmed)

            // Case A: Paragraph is HUGE (larger than target chunk size)
            // We must split this paragraph internally by sentences
            if (paragraphWordCount > targetChunkSize) {
                // 1. Flush any pending content in current chunk
                if (currentChunkText.isNotEmpty()) {
                    chunks.add(
                            createChunk(
                                    currentChunkText,
                                    chunkIndex++,
                                    tokenStart,
                                    currentChunkWordCount
                            )
                    )
                    tokenStart += currentChunkWordCount
                    currentChunkText = StringBuilder()
                    currentChunkWordCount = 0
                }

                // 2. Split the huge paragraph by sentences
                val sentenceChunks = chunkBySentences(paragraphTrimmed, targetChunkSize)

                // 3. Add all resulting chunks (re-indexing them)
                for (sChunk in sentenceChunks) {
                    // Adjust the sentence chunk's relative start to absolute start
                    val absoluteStart = tokenStart + sChunk.tokenStart
                    val absoluteEnd = tokenStart + sChunk.tokenEnd

                    chunks.add(
                            TextChunk(
                                    text = sChunk.text,
                                    index = chunkIndex++,
                                    tokenStart = absoluteStart,
                                    tokenEnd = absoluteEnd
                            )
                    )
                }
                tokenStart += paragraphWordCount
            }
            // Case B: Adding this paragraph exceeds chunk size -> Flush and start new
            else if (currentChunkWordCount + paragraphWordCount > targetChunkSize &&
                            currentChunkText.isNotEmpty()
            ) {
                chunks.add(
                        createChunk(
                                currentChunkText,
                                chunkIndex++,
                                tokenStart,
                                currentChunkWordCount
                        )
                )

                tokenStart += currentChunkWordCount
                currentChunkText = StringBuilder(paragraphTrimmed)
                currentChunkWordCount = paragraphWordCount
            }
            // Case C: Fits in current chunk
            else {
                if (currentChunkText.isNotEmpty()) currentChunkText.append("\n\n")
                currentChunkText.append(paragraphTrimmed)
                currentChunkWordCount += paragraphWordCount
            }
        }

        // Flush remaining
        if (currentChunkText.isNotEmpty()) {
            chunks.add(createChunk(currentChunkText, chunkIndex, tokenStart, currentChunkWordCount))
        }

        return chunks
    }

    private fun createChunk(sb: StringBuilder, index: Int, start: Int, length: Int): TextChunk {
        return TextChunk(
                text = sb.toString(),
                index = index,
                tokenStart = start,
                tokenEnd = start + length - 1
        )
    }

    private fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        return text.split(Regex("\\s+")).size
    }

    /** Split text by sentences, grouping into chunks of approximately target size. */
    fun chunkBySentences(text: String, targetChunkSize: Int = DEFAULT_CHUNK_SIZE): List<TextChunk> {
        if (text.isBlank()) return emptyList()

        // Split by sentence boundaries. (?<=[.!?]) keeps the delimiter.
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        val chunks = mutableListOf<TextChunk>()
        var currentChunk = StringBuilder()
        var chunkIndex = 0
        var tokenStart = 0
        var currentTokenCount = 0

        for (sentence in sentences) {
            val sentenceWordCount = sentence.split(Regex("\\s+")).size

            if (currentTokenCount + sentenceWordCount > targetChunkSize && currentChunk.isNotEmpty()
            ) {
                // Save current chunk and start new one
                chunks.add(
                        TextChunk(
                                text = currentChunk.toString().trim(),
                                index = chunkIndex,
                                tokenStart = tokenStart,
                                tokenEnd = tokenStart + currentTokenCount - 1
                        )
                )
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
            chunks.add(
                    TextChunk(
                            text = currentChunk.toString().trim(),
                            index = chunkIndex,
                            tokenStart = tokenStart,
                            tokenEnd = tokenStart + currentTokenCount - 1
                    )
            )
        }

        return chunks
    }
}

data class TextChunk(val text: String, val index: Int, val tokenStart: Int, val tokenEnd: Int)
