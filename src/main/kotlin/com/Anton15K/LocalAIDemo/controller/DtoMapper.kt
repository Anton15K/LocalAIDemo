package com.Anton15K.LocalAIDemo.controller

import com.Anton15K.LocalAIDemo.domain.Lecture
import com.Anton15K.LocalAIDemo.domain.Problem
import com.Anton15K.LocalAIDemo.domain.Theme
import com.Anton15K.LocalAIDemo.dto.*
import com.Anton15K.LocalAIDemo.service.ProblemSearchResult
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component

/**
 * Mapper for converting domain entities to DTOs.
 */
@Component
class DtoMapper(private val objectMapper: ObjectMapper) {

    fun toLectureResponse(lecture: Lecture): LectureResponse {
        return LectureResponse(
            id = lecture.id!!,
            title = lecture.title,
            status = lecture.status,
            source = lecture.source,
            uploadedBy = lecture.uploadedBy,
            errorMessage = lecture.errorMessage,
            createdAt = lecture.createdAt,
            updatedAt = lecture.updatedAt,
            themeCount = lecture.themes.size
        )
    }

    fun toLectureDetailResponse(lecture: Lecture, themes: List<Theme>): LectureDetailResponse {
        return LectureDetailResponse(
            id = lecture.id!!,
            title = lecture.title,
            status = lecture.status,
            transcript = lecture.transcript,
            source = lecture.source,
            uploadedBy = lecture.uploadedBy,
            errorMessage = lecture.errorMessage,
            structuredContent = lecture.structuredContent,
            createdAt = lecture.createdAt,
            updatedAt = lecture.updatedAt,
            themes = themes.map { toThemeResponse(it) }
        )
    }

    fun toThemeResponse(theme: Theme): ThemeResponse {
        return ThemeResponse(
            id = theme.id!!,
            name = theme.name,
            confidence = theme.confidence,
            summary = theme.summary,
            keywords = emptyList(),
            mappedTopic = theme.mappedTopic
        )
    }

    fun toProblemResponse(problem: Problem): ProblemResponse {
        return ProblemResponse(
            id = problem.id!!,
            sourceId = problem.sourceId,
            statement = problem.statement,
            solution = problem.solution,
            topic = problem.topic,
            subtopic = problem.subtopic,
            difficulty = problem.difficulty
        )
    }

    fun toProblemSearchResultResponse(result: ProblemSearchResult): ProblemSearchResultResponse {
        return ProblemSearchResultResponse(
            problem = toProblemResponse(result.problem),
            score = result.score,
            matchedTheme = result.matchedTheme
        )
    }

    fun <T, R> toPageResponse(page: Page<T>, mapper: (T) -> R): PageResponse<R> {
        return PageResponse(
            content = page.content.map(mapper),
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            first = page.isFirst,
            last = page.isLast
        )
    }

    private fun parseKeywords(keywordsJson: String?): List<String> {
        if (keywordsJson.isNullOrBlank()) return emptyList()
        return try {
            objectMapper.readValue<List<String>>(keywordsJson)
        } catch (e: Exception) {
            // Fallback: try simple parsing
            keywordsJson
                .trim('[', ']')
                .split(",")
                .map { it.trim().trim('"') }
                .filter { it.isNotBlank() }
        }
    }
}
