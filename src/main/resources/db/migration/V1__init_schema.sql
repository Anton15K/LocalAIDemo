-- V1__init_schema.sql
-- Initialize pgvector extension and create base tables

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Problems table (MATH dataset)
CREATE TABLE problems (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id VARCHAR(255) NOT NULL UNIQUE,
    statement TEXT NOT NULL,
    solution TEXT,
    topic VARCHAR(255) NOT NULL,
    subtopic VARCHAR(255),
    difficulty INTEGER,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index on topic for fast filtering
CREATE INDEX idx_problems_topic ON problems(topic);
CREATE INDEX idx_problems_difficulty ON problems(difficulty);
CREATE INDEX idx_problems_source_id ON problems(source_id);

-- Lectures table
CREATE TABLE lectures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(500) NOT NULL,
    source VARCHAR(255),
    uploaded_by VARCHAR(255),
    transcript TEXT,
    transcript_uri VARCHAR(500),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lectures_status ON lectures(status);
CREATE INDEX idx_lectures_uploaded_by ON lectures(uploaded_by);

-- Lecture chunks for embedding
CREATE TABLE lecture_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lecture_id UUID NOT NULL REFERENCES lectures(id) ON DELETE CASCADE,
    chunk_text TEXT NOT NULL,
    chunk_index INTEGER NOT NULL,
    token_start INTEGER,
    token_end INTEGER,
    theme_tags JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lecture_chunks_lecture_id ON lecture_chunks(lecture_id);

-- Themes extracted from lectures
CREATE TABLE themes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lecture_id UUID NOT NULL REFERENCES lectures(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    confidence DOUBLE PRECISION,
    summary TEXT,
    keywords JSONB,
    mapped_topic VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_themes_lecture_id ON themes(lecture_id);
CREATE INDEX idx_themes_mapped_topic ON themes(mapped_topic);
