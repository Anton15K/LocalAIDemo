package com.Anton15K.LocalAIDemo.service

import org.slf4j.LoggerFactory
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service

/**
 * Service for generating and managing embeddings using Ollama via Spring AI.
 */
@Service
class EmbeddingService(
    private val embeddingModel: EmbeddingModel,
    private val vectorStore: VectorStore
) {
    private val logger = LoggerFactory.getLogger(EmbeddingService::class.java)

    /**
     * Generate embedding vector for a single text.
     */
    fun embed(text: String): FloatArray {
        logger.debug("Generating embedding for text of length: ${text.length}")
        val response = embeddingModel.embed(text)
        return response
    }

    /**
     * Generate embeddings for multiple texts in batch.
     */
    fun embedBatch(texts: List<String>): List<FloatArray> {
        logger.debug("Generating embeddings for ${texts.size} texts")
        val response = embeddingModel.embed(texts)
        return response
    }

    /**
     * Store a document with its embedding in the vector store.
     */
    fun storeDocument(id: String, content: String, metadata: Map<String, Any> = emptyMap()) {
        val document = Document(id, content, metadata)
        vectorStore.add(listOf(document))
        logger.debug("Stored document with id: $id")
    }

    /**
     * Store multiple documents in the vector store.
     */
    fun storeDocuments(documents: List<Document>) {
        vectorStore.add(documents)
        logger.debug("Stored ${documents.size} documents")
    }

    /**
     * Search for similar documents in the vector store.
     */
    fun similaritySearch(query: String, topK: Int = 10): List<Document> {
        logger.debug("Performing similarity search for query of length: ${query.length}")
        val searchRequest = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .build()
        return vectorStore.similaritySearch(searchRequest)
    }

    /**
     * Delete documents from the vector store.
     */
    fun deleteDocuments(ids: List<String>) {
        vectorStore.delete(ids)
        logger.debug("Deleted ${ids.size} documents from vector store")
    }
}
