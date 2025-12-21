package com.Anton15K.LocalAIDemo.config

import org.springframework.context.annotation.Configuration

/**
 * Configuration for AI components (Ollama, embeddings, vector store).
 * Spring AI auto-configures ChatClient.Builder and EmbeddingModel beans.
 */
@Configuration
class AiConfig {
    // ChatClient.Builder is auto-configured by Spring AI
    // No additional beans needed
}
