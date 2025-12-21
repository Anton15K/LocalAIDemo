package com.Anton15K.LocalAIDemo.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Represents a theme extracted from a lecture.
 */
@Entity
@Table(name = "themes")
data class Theme(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecture_id", nullable = false)
    val lecture: Lecture,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "confidence")
    val confidence: Double? = null,

    @Column(name = "summary", columnDefinition = "TEXT")
    val summary: String? = null,

    @Column(name = "keywords", columnDefinition = "JSONB")
    val keywords: String? = null,

    @Column(name = "mapped_topic")
    val mappedTopic: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
) {
    constructor() : this(
        lecture = Lecture(),
        name = ""
    )
}
