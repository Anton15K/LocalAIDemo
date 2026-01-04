package com.Anton15K.LocalAIDemo.dto

import com.Anton15K.LocalAIDemo.domain.LectureStatus
import java.time.Instant
import java.util.UUID

// ============================================
// Lecture DTOs
// ============================================

data class CreateLectureRequest(
    val title: String,
    val transcript: String,
    val uploadedBy: String? = null
)

data class LectureResponse(
    val id: UUID,
    val title: String,
    val status: LectureStatus,
    val source: String?,
    val uploadedBy: String?,
    val errorMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val themeCount: Int = 0
)

data class LectureDetailResponse(
    val id: UUID,
    val title: String,
    val status: LectureStatus,
    val transcript: String?,
    val source: String?,
    val uploadedBy: String?,
    val errorMessage: String?,
    val structuredContent: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val themes: List<ThemeResponse> = emptyList()
)

data class ThemeResponse(
    val id: UUID,
    val name: String,
    val confidence: Double?,
    val summary: String?,
    val keywords: List<String>,
    val mappedTopic: String?
)

// ============================================
// Problem DTOs
// ============================================

data class ProblemResponse(
    val id: UUID,
    val sourceId: String,
    val statement: String,
    val solution: String?,
    val topic: String,
    val subtopic: String?,
    val difficulty: Int?
)

data class ProblemSearchResultResponse(
    val problem: ProblemResponse,
    val score: Double,
    val matchedTheme: String?
)

data class ImportProblemsRequest(
    val problems: List<ProblemImportItem>
)

data class ProblemImportItem(
    val sourceId: String,
    val statement: String,
    val solution: String? = null,
    val topic: String,
    val subtopic: String? = null,
    val difficulty: Int? = null
)

data class ImportResultResponse(
    val imported: Int,
    val skipped: Int,
    val failed: Int,
    val message: String
)

// ============================================
// Health DTOs
// ============================================

data class HealthResponse(
    val status: String,
    val components: Map<String, ComponentHealth>
)

data class ComponentHealth(
    val status: String,
    val details: Map<String, Any>? = null
)

// ============================================
// Pagination wrapper
// ============================================

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean
)
