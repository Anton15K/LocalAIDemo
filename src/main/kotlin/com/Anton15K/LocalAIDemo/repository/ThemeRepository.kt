package com.Anton15K.LocalAIDemo.repository

import com.Anton15K.LocalAIDemo.domain.Theme
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ThemeRepository : JpaRepository<Theme, UUID> {
    
    fun findByLectureId(lectureId: UUID): List<Theme>
    
    fun deleteByLectureId(lectureId: UUID)
    
    @Query("SELECT DISTINCT t.mappedTopic FROM Theme t WHERE t.mappedTopic IS NOT NULL")
    fun findAllMappedTopics(): List<String>
}
