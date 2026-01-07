package com.Anton15K.LocalAIDemo.service

import com.Anton15K.LocalAIDemo.domain.Problem
import com.Anton15K.LocalAIDemo.repository.ProblemRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.ai.document.Document
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.util.UUID

class ProblemRetrievalServiceTest {

    private val problemRepository: ProblemRepository = mockk()
    private val embeddingService: EmbeddingService = mockk()
    private val topicMatchBaseScore = 0.6
    
    private val service = ProblemRetrievalService(
        problemRepository = problemRepository,
        embeddingService = embeddingService,
        topicMatchBaseScore = topicMatchBaseScore
    )

    @Test
    fun `findByTopic should return problems from repository`() {
        // Given
        val topic = "Algebra"
        val pageable = PageRequest.of(0, 10)
        val problems = listOf(
            createProblem(topic = topic),
            createProblem(topic = topic)
        )
        val expectedPage = PageImpl(problems, pageable, problems.size.toLong())
        
        every { problemRepository.findByTopic(topic, pageable) } returns expectedPage

        // When
        val result = service.findByTopic(topic, pageable)

        // Then
        assertEquals(expectedPage, result)
        verify { problemRepository.findByTopic(topic, pageable) }
    }

    @Test
    fun `findByTopics with empty list should return empty page`() {
        // Given
        val pageable = PageRequest.of(0, 10)

        // When
        val result = service.findByTopics(emptyList(), pageable)

        // Then
        assertEquals(0, result.totalElements)
        assertTrue(result.content.isEmpty())
    }

    @Test
    fun `findByTopics should return problems from repository`() {
        // Given
        val topics = listOf("Algebra", "Geometry")
        val pageable = PageRequest.of(0, 10)
        val problems = listOf(
            createProblem(topic = "Algebra"),
            createProblem(topic = "Geometry")
        )
        val expectedPage = PageImpl(problems, pageable, problems.size.toLong())
        
        every { problemRepository.findByTopicIn(topics, pageable) } returns expectedPage

        // When
        val result = service.findByTopics(topics, pageable)

        // Then
        assertEquals(expectedPage, result)
        verify { problemRepository.findByTopicIn(topics, pageable) }
    }

    @Test
    fun `searchByThemes with empty themes should return empty page`() {
        // Given
        val pageable = PageRequest.of(0, 10)

        // When
        val result = service.searchByThemes(emptyList(), pageable = pageable)

        // Then
        assertEquals(0, result.totalElements)
        assertTrue(result.content.isEmpty())
    }

    @Test
    fun `searchByThemes should perform semantic search and filter by mapped topics`() {
        // Given
        val theme = ExtractedTheme(
            name = "Linear Algebra",
            confidence = 0.9,
            summary = "Vectors and matrices",
            keywords = listOf("vector", "matrix", "transformation"),
            mappedTopic = "Linear Algebra"
        )
        val pageable = PageRequest.of(0, 10)
        val topK = 5
        
        val problem = createProblem(topic = "Linear Algebra")
        val document = Document(
            problem.id.toString(),
            "Topic: Linear Algebra\nProblem: Solve the system of equations",
            mapOf("score" to 0.85)
        )
        
        every { embeddingService.similaritySearch(any(), topK) } returns listOf(document)
        every { problemRepository.findById(problem.id!!) } returns java.util.Optional.of(problem)

        // When
        val result = service.searchByThemes(listOf(theme), topK = topK, pageable = pageable)

        // Then
        assertEquals(1, result.totalElements)
        val searchResult = result.content.first()
        assertEquals(problem, searchResult.problem)
        assertEquals(0.85 * theme.confidence, searchResult.score)
        assertEquals(theme.name, searchResult.matchedTheme)
    }

    @Test
    fun `hybridSearch should combine topic-based and semantic search`() {
        // Given
        val theme = ExtractedTheme(
            name = "Calculus",
            confidence = 0.8,
            summary = "Derivatives and integrals",
            keywords = listOf("derivative", "integral", "limit"),
            mappedTopic = "Calculus"
        )
        val pageable = PageRequest.of(0, 10)
        val topK = 5
        
        val topicProblem = createProblem(topic = "Calculus")
        val semanticProblem = createProblem(topic = "Calculus")
        
        val topicPage = PageImpl(listOf(topicProblem), Pageable.unpaged(), 1)
        val document = Document(
            semanticProblem.id.toString(),
            "Topic: Calculus\nProblem: Find the derivative",
            mapOf("score" to 0.75)
        )
        
        every { problemRepository.findByTopicIn(listOf("Calculus"), Pageable.unpaged()) } returns topicPage
        every { embeddingService.similaritySearch(any(), topK) } returns listOf(document)
        every { problemRepository.findById(topicProblem.id!!) } returns java.util.Optional.of(topicProblem)
        every { problemRepository.findById(semanticProblem.id!!) } returns java.util.Optional.of(semanticProblem)

        // When
        val result = service.hybridSearch(listOf(theme), topK = topK, pageable = pageable)

        // Then
        assertEquals(2, result.totalElements)
        
        val topicResult = result.content.find { it.problem.id == topicProblem.id }
        assertNotNull(topicResult)
        assertEquals(topicMatchBaseScore, topicResult.score)
        
        val semanticResult = result.content.find { it.problem.id == semanticProblem.id }
        assertNotNull(semanticResult)
        assertEquals(0.75 * theme.confidence, semanticResult.score)
    }

    @Test
    fun `indexProblem should store document with correct metadata`() {
        // Given
        val problem = createProblem()
        every { embeddingService.storeDocument(any(), any(), any()) } returns Unit

        // When
        service.indexProblem(problem)

        // Then
        verify {
            embeddingService.storeDocument(
                problem.id.toString(),
                "Topic: ${problem.topic}\nProblem: ${problem.statement}",
                mapOf(
                    "sourceId" to problem.sourceId,
                    "topic" to problem.topic,
                    "difficulty" to (problem.difficulty ?: 0),
                    "type" to "problem"
                )
            )
        }
    }

    @Test
    fun `indexProblems should store multiple documents`() {
        // Given
        val problems = listOf(createProblem(), createProblem())
        every { embeddingService.storeDocuments(any()) } returns Unit

        // When
        service.indexProblems(problems)

        // Then
        verify { embeddingService.storeDocuments(any()) }
    }

    @Test
    fun `buildSearchQuery should include mapped topic when available`() {
        // Given
        val theme = ExtractedTheme(
            name = "Geometry",
            confidence = 0.9,
            summary = "Shapes and angles",
            keywords = listOf("triangle", "circle", "angle"),
            mappedTopic = "Geometry"
        )

        // When
        val query = service.buildSearchQuery(theme)

        // Then
        assertTrue(query.contains("Topic: Geometry"))
        assertTrue(query.contains("Geometry"))
        assertTrue(query.contains("triangle circle angle"))
        assertTrue(query.contains("Shapes and angles"))
    }

    @Test
    fun `buildProblemContent should include topic and statement`() {
        // Given
        val problem = createProblem(
            topic = "Algebra",
            subtopic = "Quadratic Equations",
            statement = "Solve x^2 + 5x + 6 = 0"
        )

        // When
        val content = service.buildProblemContent(problem)

        // Then
        assertEquals(
            "Topic: Algebra\nSubtopic: Quadratic Equations\nProblem: Solve x^2 + 5x + 6 = 0",
            content
        )
    }

    @Test
    fun `extractProblemId should handle prefixed and unprefixed UUIDs`() {
        // Given
        val problemId = UUID.randomUUID()
        val prefixedId = "problem:$problemId"
        val unprefixedId = problemId.toString()

        // When
        val result1 = service.extractProblemId(prefixedId)
        val result2 = service.extractProblemId(unprefixedId)
        val result3 = service.extractProblemId("invalid-uuid")

        // Then
        assertEquals(problemId, result1)
        assertEquals(problemId, result2)
        assertEquals(null, result3)
    }

    @Test
    fun `clampScore should limit values between 0 and 1`() {
        // Given
        val testCases = listOf(
            -0.5 to 0.0,
            0.3 to 0.3,
            1.5 to 1.0,
            0.0 to 0.0,
            1.0 to 1.0
        )

        // When & Then
        testCases.forEach { (input, expected) ->
            val result = service.clampScore(input)
            assertEquals(expected, result, "clampScore($input) should be $expected")
        }
    }

    private fun createProblem(
        topic: String = "Test Topic",
        subtopic: String? = null,
        statement: String = "Test problem statement",
        difficulty: Int? = 1
    ): Problem {
        return Problem(
            id = UUID.randomUUID(),
            sourceId = "test-${UUID.randomUUID()}",
            statement = statement,
            solution = "Test solution",
            topic = topic,
            subtopic = subtopic,
            difficulty = difficulty,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
}