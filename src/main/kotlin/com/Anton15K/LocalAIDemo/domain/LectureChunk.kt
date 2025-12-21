package com.Anton15K.LocalAIDemo.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Represents a chunk of a lecture transcript for embedding and retrieval.
 */
@Entity
@Table(name = "lecture_chunks")
data class LectureChunk(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecture_id", nullable = false)
    val lecture: Lecture,

    @Column(name = "chunk_text", nullable = false, columnDefinition = "TEXT")
    val chunkText: String,

    @Column(name = "chunk_index", nullable = false)
    val chunkIndex: Int,

    @Column(name = "token_start")
    val tokenStart: Int? = null,

    @Column(name = "token_end")
    val tokenEnd: Int? = null,

    @Column(name = "theme_tags", columnDefinition = "JSONB")
    val themeTags: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
) {
    constructor() : this(
        lecture = Lecture(),
        chunkText = "",
        chunkIndex = 0
    )
}
