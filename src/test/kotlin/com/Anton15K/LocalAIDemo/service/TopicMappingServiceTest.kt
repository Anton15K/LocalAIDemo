package com.Anton15K.LocalAIDemo.service

import com.Anton15K.LocalAIDemo.repository.ProblemRepository
import org.mockito.Mockito.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TopicMappingServiceTest {

    @Test
    fun mapThemesToExistingTopics_noThemes_returnsSameList() {
        val repo = mock(ProblemRepository::class.java)
        val service = TopicMappingService(repo, cacheTtlSeconds = 600, maxCandidates = 100, minScore = 3)

        val out = service.mapThemesToExistingTopics(emptyList())

        assertTrue(out.isEmpty())
        verifyNoInteractions(repo)
    }

    @Test
    fun mapThemesToExistingTopics_emptyCatalog_returnsThemesUnchanged() {
        val repo = mock(ProblemRepository::class.java)
        `when`(repo.findAllTopics()).thenReturn(emptyList())
        val service = TopicMappingService(repo, cacheTtlSeconds = 600, maxCandidates = 100, minScore = 3)

        val themes = listOf(
            ExtractedTheme(
                name = "Vectors in 2D",
                confidence = 0.9,
                summary = "Dot product and angles.",
                keywords = listOf("vector", "angle"),
                mappedTopic = null
            )
        )

        val out = service.mapThemesToExistingTopics(themes)

        assertEquals(themes, out)
        verify(repo, times(1)).findAllTopics()
    }

    @Test
    fun mapThemesToExistingTopics_disambiguatesGeometryVsLinearAlgebra() {
        val repo = mock(ProblemRepository::class.java)
        `when`(repo.findAllTopics()).thenReturn(
            listOf(
                "Geometry",
                "Geometry -> Vectors",
                "Linear Algebra",
                "Linear Algebra -> Matrices",
                "Calculus"
            )
        )
        val service = TopicMappingService(repo, cacheTtlSeconds = 600, maxCandidates = 1000, minScore = 3)

        val geometryTheme = ExtractedTheme(
            name = "Vectors and angles",
            confidence = 0.9,
            summary = "We compute dot product, distances, and angles in 2D coordinates.",
            keywords = listOf("dot", "distance", "2d", "coordinates"),
            mappedTopic = null
        )

        val laTheme = ExtractedTheme(
            name = "Eigenvalues and diagonalization",
            confidence = 0.9,
            summary = "Matrix rank, eigenvectors, basis and linear transformations.",
            keywords = listOf("matrix", "rank", "eigenvalue", "basis"),
            mappedTopic = null
        )

        val out = service.mapThemesToExistingTopics(listOf(geometryTheme, laTheme))

        assertEquals(2, out.size)
        assertEquals("Geometry -> Vectors", out[0].mappedTopic)
        assertEquals("Linear Algebra", out[1].mappedTopic)
    }

    @Test
    fun mapThemesToExistingTopics_respectsMinScore_andCanReturnNull() {
        val repo = mock(ProblemRepository::class.java)
        `when`(repo.findAllTopics()).thenReturn(listOf("Geometry", "Linear Algebra"))

        // Set minScore very high so weak matches drop to null
        val service = TopicMappingService(repo, cacheTtlSeconds = 600, maxCandidates = 100, minScore = 50)

        val weakTheme = ExtractedTheme(
            name = "Misc",
            confidence = 0.5,
            summary = "",
            keywords = emptyList(),
            mappedTopic = null
        )

        val out = service.mapThemesToExistingTopics(listOf(weakTheme))

        assertEquals(1, out.size)
        assertNull(out.first().mappedTopic)
    }

    @Test
    fun mapThemesToExistingTopics_usesCacheWithinTtl() {
        val repo = mock(ProblemRepository::class.java)
        `when`(repo.findAllTopics()).thenReturn(listOf("Geometry", "Linear Algebra"))

        val service = TopicMappingService(repo, cacheTtlSeconds = 600, maxCandidates = 100, minScore = 3)

        val theme = ExtractedTheme(
            name = "Angles",
            confidence = 0.9,
            summary = "Triangle angles and area.",
            keywords = listOf("triangle", "angle"),
            mappedTopic = null
        )

        service.mapThemesToExistingTopics(listOf(theme))
        service.mapThemesToExistingTopics(listOf(theme))

        verify(repo, times(1)).findAllTopics()
    }
}
