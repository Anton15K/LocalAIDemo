package com.Anton15K.LocalAIDemo.repository

import com.Anton15K.LocalAIDemo.domain.LectureChunk
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface LectureChunkRepository : JpaRepository<LectureChunk, UUID> {
    
    fun findByLectureIdOrderByChunkIndex(lectureId: UUID): List<LectureChunk>
    
    fun deleteByLectureId(lectureId: UUID)
}
