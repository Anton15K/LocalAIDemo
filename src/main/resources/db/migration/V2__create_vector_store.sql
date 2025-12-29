-- V2__create_vector_store.sql
-- Create vector store table for Spring AI embeddings

-- Vector store for embeddings (used by Spring AI PGVectorStore)
CREATE TABLE IF NOT EXISTS vector_store (
    id VARCHAR(500) PRIMARY KEY,
    content TEXT NOT NULL,
    metadata JSON,
    embedding vector(768) NOT NULL
);

-- Index for similarity search
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx 
ON vector_store 
USING hnsw (embedding vector_cosine_ops);
