package com.Anton15K.LocalAIDemo.repository

import com.Anton15K.LocalAIDemo.domain.Problem
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ProblemRepository : JpaRepository<Problem, UUID> {
    
    fun findBySourceId(sourceId: String): Problem?
    
    fun findByTopic(topic: String, pageable: Pageable): Page<Problem>
    
    fun findByTopicIn(topics: List<String>, pageable: Pageable): Page<Problem>
    
    fun findByDifficulty(difficulty: Int, pageable: Pageable): Page<Problem>
    
    fun findByTopicAndDifficulty(topic: String, difficulty: Int, pageable: Pageable): Page<Problem>
    
    @Query("SELECT DISTINCT p.topic FROM Problem p ORDER BY p.topic")
    fun findAllTopics(): List<String>
    
    @Query("SELECT DISTINCT p.topic FROM Problem p ORDER BY p.topic")
    fun findDistinctTopics(): List<String>
    
    @Query("SELECT p FROM Problem p WHERE LOWER(p.topic) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    fun findByTopicContaining(@Param("keyword") keyword: String, pageable: Pageable): Page<Problem>
    
    @Query("SELECT p FROM Problem p WHERE LOWER(p.topic) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    fun findByTopicContainingIgnoreCase(@Param("keyword") keyword: String, pageable: Pageable): Page<Problem>
    
    fun existsBySourceId(sourceId: String): Boolean

    fun findBySourceIdIn(sourceIds: List<String>): List<Problem>

    fun countBySourceIdStartingWith(prefix: String): Long
}
