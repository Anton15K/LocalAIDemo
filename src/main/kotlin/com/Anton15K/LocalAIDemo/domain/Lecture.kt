package com.Anton15K.LocalAIDemo.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Processing status for a lecture.
 */
enum class LectureStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}

/**
 * Represents a lecture that has been uploaded for analysis.
 */
@Entity
@Table(name = "lectures")
data class Lecture(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "title", nullable = false)
    val title: String,

    @Column(name = "source")
    val source: String? = null,

    @Column(name = "uploaded_by")
    val uploadedBy: String? = null,

    @Column(name = "transcript", columnDefinition = "TEXT")
    val transcript: String? = null,

    @Column(name = "transcript_uri")
    val transcriptUri: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: LectureStatus = LectureStatus.PENDING,

    @Column(name = "error_message", columnDefinition = "TEXT")
    val errorMessage: String? = null,

    @Column(name = "structured_content", columnDefinition = "TEXT")
    val structuredContent: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),

    @OneToMany(mappedBy = "lecture", cascade = [CascadeType.ALL], orphanRemoval = true)
    val chunks: MutableList<LectureChunk> = mutableListOf(),

    @OneToMany(mappedBy = "lecture", cascade = [CascadeType.ALL], orphanRemoval = true)
    val themes: MutableList<Theme> = mutableListOf()
) {
    constructor() : this(title = "")
}
