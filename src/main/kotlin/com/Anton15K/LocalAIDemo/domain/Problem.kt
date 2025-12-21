package com.Anton15K.LocalAIDemo.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Represents a math problem from the MATH dataset.
 */
@Entity
@Table(name = "problems")
data class Problem(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "source_id", nullable = false, unique = true)
    val sourceId: String,

    @Column(name = "statement", nullable = false, columnDefinition = "TEXT")
    val statement: String,

    @Column(name = "solution", columnDefinition = "TEXT")
    val solution: String? = null,

    @Column(name = "topic", nullable = false)
    val topic: String,

    @Column(name = "subtopic")
    val subtopic: String? = null,

    @Column(name = "difficulty")
    val difficulty: Int? = null,

    @Column(name = "metadata", columnDefinition = "JSONB")
    val metadata: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
) {
    // No-arg constructor for JPA
    constructor() : this(
        sourceId = "",
        statement = "",
        topic = ""
    )
}
