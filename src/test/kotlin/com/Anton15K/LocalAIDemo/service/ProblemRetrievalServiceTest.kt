package com.Anton15K.LocalAIDemo.service

import com.Anton15K.LocalAIDemo.domain.Problem
import com.Anton15K.LocalAIDemo.repository.ProblemRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.ai.document.Document
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable

class ProblemRetrievalServiceTest {

    private val problemRepository: ProblemRepository = mockk(relaxed = true)
    private val embeddingService: EmbeddingService = mockk(relaxed = true)

    private val service =
        ProblemRetrievalService(
            problemRepository = problemRepository,
            embeddingService = embeddingService,
            topicMatchBaseScore = 0.6
        )

    @Test
    fun findByTopic_delegatesToRepository() {
        val pageable = PageRequest.of(0, 5)
        val topic = "Algebra"
        val problems = listOf(createProblem(topic = topic), createProblem(topic = topic))
        val expected = PageImpl(problems, pageable, problems.size.toLong())

        every { problemRepository.findByTopic(topic, pageable) } returns expected

        val result = service.findByTopic(topic, pageable)

        assertEquals(expected, result)
        verify(exactly = 1) { problemRepository.findByTopic(topic, pageable) }
    }

    @Test
    fun findByTopics_empty_returnsEmptyPage_andDoesNotHitRepository() {
        val pageable = PageRequest.of(0, 5)

        val result = service.findByTopics(emptyList(), pageable)

        assertEquals(0, result.totalElements)
        assertTrue(result.content.isEmpty())
        verify(exactly = 0) { problemRepository.findByTopicIn(any(), any()) }
    }

    @Test
    fun findByTopics_nonEmpty_delegatesToRepository() {
        val pageable = PageRequest.of(0, 5)
        val topics = listOf("Algebra", "Geometry")
        val problems = listOf(createProblem(topic = "Algebra"), createProblem(topic = "Geometry"))
        val expected = PageImpl(problems, pageable, problems.size.toLong())

        every { problemRepository.findByTopicIn(topics, pageable) } returns expected

        val result = service.findByTopics(topics, pageable)

        assertEquals(expected, result)
        verify(exactly = 1) { problemRepository.findByTopicIn(topics, pageable) }
    }

    @Test
    fun searchByThemes_empty_returnsEmpty_andDoesNotCallEmbedding() {
        val pageable = PageRequest.of(0, 10)

        val result = service.searchByThemes(emptyList(), pageable = pageable)

        assertEquals(0, result.totalElements)
        assertTrue(result.content.isEmpty())
        verify(exactly = 0) { embeddingService.similaritySearch(any(), any()) }
    }

    @Test
    fun searchByThemes_dedupesProblemsAcrossThemes_andWeightsByConfidence() {
        val pageable = PageRequest.of(0, 10)
        val problemId = UUID.randomUUID()
        val problem = createProblem(id = problemId, topic = "Geometry")

        val themeA = ExtractedTheme(
            name = "Triangles",
            confidence = 0.5,
            summary = "",
            keywords = listOf("triangle"),
            mappedTopic = null
        )
        val themeB = themeA.copy(name = "Angles", confidence = 0.9)

        val docs =
            listOf(
                Document(problemId.toString(), "irrelevant", mapOf("score" to 0.8))
            )

        every { embeddingService.similaritySearch(any(), any()) } returns docs
        every { problemRepository.findById(problemId) } returns Optional.of(problem)

        val result = service.searchByThemes(listOf(themeA, themeB), topK = 10, pageable = pageable)

        // Same problem appears for both themes, should only be returned once.
        assertEquals(1, result.totalElements)
        val single = result.content.single()

        assertEquals(problemId, single.problem.id)
        // Score should be clamped(0.8 * confidence) for the first-seen theme.
        // We don't assert which theme wins, but score must match either.
        assertTrue(single.score == 0.4 || single.score == 0.72)

        verify(exactly = 2) { embeddingService.similaritySearch(any(), 10) }
        verify(exactly = 1) { problemRepository.findById(problemId) }
    }

    @Test
    fun searchByThemes_respectsAllowedTopicsFilter() {
        val pageable = PageRequest.of(0, 10)

        val geometryId = UUID.randomUUID()
        val algebraId = UUID.randomUUID()

        val geometryProblem = createProblem(id = geometryId, topic = "Geometry")
        val algebraProblem = createProblem(id = algebraId, topic = "Algebra")

        val themes =
            listOf(
                ExtractedTheme(
                    name = "Geometry",
                    confidence = 1.0,
                    summary = "",
                    keywords = emptyList(),
                    mappedTopic = "Geometry"
                ),
                // Unmapped theme should be constrained to allowedTopics when allowedTopics is non-empty.
                ExtractedTheme(
                    name = "General",
                    confidence = 1.0,
                    summary = "",
                    keywords = emptyList(),
                    mappedTopic = null
                )
            )

        every { embeddingService.similaritySearch(any(), any()) } answers {
            listOf(
                Document(geometryId.toString(), "", mapOf("score" to 0.8)),
                Document(algebraId.toString(), "", mapOf("score" to 0.9))
            )
        }

        every { problemRepository.findById(geometryId) } returns Optional.of(geometryProblem)
        every { problemRepository.findById(algebraId) } returns Optional.of(algebraProblem)

        val result = service.searchByThemes(themes, topK = 10, pageable = pageable)

        assertEquals(1, result.totalElements)
        assertEquals("Geometry", result.content.single().problem.topic)
    }

    @Test
    fun hybridSearch_boostsScoreWhenFoundByTopicAndSemantic_andClampsToOne() {
        val pageable = PageRequest.of(0, 10)
        val id = UUID.randomUUID()
        val problem = createProblem(id = id, topic = "Geometry")

        val theme = ExtractedTheme(
            name = "Geometry",
            confidence = 1.0,
            summary = "",
            keywords = emptyList(),
            mappedTopic = "Geometry"
        )

        val topicPage = PageImpl(listOf(problem), Pageable.unpaged(), 1)
        every { problemRepository.findByTopicIn(listOf("Geometry"), Pageable.unpaged()) } returns topicPage

        every { embeddingService.similaritySearch(any(), any()) } returns
            listOf(Document(id.toString(), "", mapOf("score" to 0.9)))

        every { problemRepository.findById(id) } returns Optional.of(problem)

        val result = service.hybridSearch(listOf(theme), topK = 10, pageable = pageable)

        assertEquals(1, result.totalElements)
        val score = result.content.single().score
        assertEquals(1.0, score)
    }

    @Test
    fun indexProblem_withNullId_doesNothing() {
        val problem = createProblem(id = null)

        service.indexProblem(problem)

        verify(exactly = 0) { embeddingService.storeDocument(any(), any(), any()) }
    }

    @Test
    fun indexProblem_storesUuidIdAndContentAndMetadata() {
        val id = UUID.randomUUID()
        val problem =
            createProblem(
                id = id,
                sourceId = "src-1",
                topic = "Algebra",
                subtopic = "Quadratics",
                statement = "Solve x^2 + 5x + 6 = 0",
                difficulty = 3
            )

        service.indexProblem(problem)

        verify(exactly = 1) {
            embeddingService.storeDocument(
                id.toString(),
                match { it.contains("Topic: Algebra") && it.contains("Subtopic: Quadratics") && it.contains("Problem: Solve") },
                match {
                    it["sourceId"] == "src-1" &&
                        it["topic"] == "Algebra" &&
                        it["difficulty"] == 3 &&
                        it["type"] == "problem"
                }
            )
        }
    }

    @Test
    fun indexProblems_skipsNullIds_andStoresDocumentsInBatch() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()

        val problems =
            listOf(
                createProblem(id = id1, sourceId = "s1"),
                createProblem(id = null, sourceId = "no-id"),
                createProblem(id = id2, sourceId = "s2")
            )

        service.indexProblems(problems)

        verify(exactly = 1) {
            embeddingService.storeDocuments(
                match { docs ->
                    docs.size == 2 &&
                        docs.all { it.metadata["type"] == "problem" } &&
                        docs.map { it.id }.toSet() == setOf(id1.toString(), id2.toString())
                }
            )
        }
    }

    @Test
    fun searchByThemes_acceptsPrefixedProblemIds() {
        val pageable = PageRequest.of(0, 10)
        val id = UUID.randomUUID()
        val problem = createProblem(id = id, topic = "Algebra")

        val theme = ExtractedTheme(
            name = "Algebra",
            confidence = 1.0,
            summary = "",
            keywords = emptyList(),
            mappedTopic = null
        )

        every { embeddingService.similaritySearch(any(), any()) } returns
            listOf(Document("problem:$id", "", mapOf("score" to 0.7)))
        every { problemRepository.findById(id) } returns Optional.of(problem)

        val result = service.searchByThemes(listOf(theme), topK = 10, pageable = pageable)

        assertEquals(1, result.totalElements)
        assertEquals(id, result.content.single().problem.id)
    }

    private fun createProblem(
        id: UUID? = UUID.randomUUID(),
        sourceId: String = "source-${UUID.randomUUID()}",
        statement: String = "Some statement",
        solution: String? = null,
        topic: String = "Topic",
        subtopic: String? = null,
        difficulty: Int? = 1
    ): Problem {
        return Problem(
            id = id,
            sourceId = sourceId,
            statement = statement,
            solution = solution,
            topic = topic,
            subtopic = subtopic,
            difficulty = difficulty,
            createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2024-01-01T00:00:00Z")
        )
    }
}
