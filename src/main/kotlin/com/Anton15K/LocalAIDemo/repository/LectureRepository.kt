package com.Anton15K.LocalAIDemo.repository

import com.Anton15K.LocalAIDemo.domain.Lecture
import com.Anton15K.LocalAIDemo.domain.LectureStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface LectureRepository : JpaRepository<Lecture, UUID> {
    
    fun findByStatus(status: LectureStatus, pageable: Pageable): Page<Lecture>
    
    fun findByUploadedBy(uploadedBy: String, pageable: Pageable): Page<Lecture>
    
    fun countByStatus(status: LectureStatus): Long
}
