package com.Anton15K.LocalAIDemo.controller

import com.Anton15K.LocalAIDemo.repository.LectureRepository
import com.Anton15K.LocalAIDemo.repository.ProblemRepository
import com.Anton15K.LocalAIDemo.repository.ThemeRepository
import com.Anton15K.LocalAIDemo.service.LectureProcessingService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.UUID

@Controller
class WebController(
    private val lectureRepository: LectureRepository,
    private val problemRepository: ProblemRepository,
    private val themeRepository: ThemeRepository,
    private val lectureProcessingService: LectureProcessingService
) {

    @GetMapping("/")
    fun home(model: Model): String {
        val lectureCount = lectureRepository.count()
        val problemCount = problemRepository.count()
        val themeCount = themeRepository.count()
        
        val recentLectures = lectureRepository.findAll(
            PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).content
        
        model.addAttribute("lectureCount", lectureCount)
        model.addAttribute("problemCount", problemCount)
        model.addAttribute("themeCount", themeCount)
        model.addAttribute("recentLectures", recentLectures)
        
        return "home"
    }

    // ============ LECTURES ============

    @GetMapping("/lectures")
    fun listLectures(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        model: Model
    ): String {
        val lectures = lectureRepository.findAll(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        )
        
        model.addAttribute("lectures", lectures)
        
        return "lectures/list"
    }

    @GetMapping("/lectures/new")
    fun newLecture(): String {
        return "lectures/new"
    }

    @PostMapping("/lectures/create")
    fun createLecture(
        @RequestParam title: String,
        @RequestParam transcript: String,
        @RequestParam(required = false) uploadedBy: String?,
        @RequestParam action: String,
        redirectAttributes: RedirectAttributes
    ): String {
        return try {
            val lecture = lectureProcessingService.createLecture(title, transcript, uploadedBy)
            
            if (action == "analyze") {
                // Process asynchronously - just start it and redirect
                Thread {
                    try {
                        lectureProcessingService.processLecture(lecture.id!!)
                    } catch (e: Exception) {
                        // Error will be stored in the lecture status
                    }
                }.start()
                redirectAttributes.addFlashAttribute("message", "Lecture created and analysis started!")
            } else {
                redirectAttributes.addFlashAttribute("message", "Lecture saved as draft!")
            }
            
            "redirect:/lectures/${lecture.id}"
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "Failed to create lecture: ${e.message}")
            "redirect:/lectures/new"
        }
    }

    @GetMapping("/lectures/{id}")
    fun lectureDetail(@PathVariable id: UUID, model: Model): String {
        val lecture = lectureRepository.findById(id)
            .orElseThrow { NoSuchElementException("Lecture not found with id: $id") }
        
        val themes = themeRepository.findByLectureId(id)
        
        // Get recommended problems from themes
        val recommendedProblems = themes.flatMap { theme ->
            problemRepository.findByTopicContainingIgnoreCase(theme.name, PageRequest.of(0, 5)).content
        }.distinctBy { it.id }.take(10)
        
        model.addAttribute("lecture", lecture)
        model.addAttribute("themes", themes)
        model.addAttribute("recommendedProblems", recommendedProblems)
        
        return "lectures/detail"
    }

    // ============ PROBLEMS ============

    @GetMapping("/problems")
    fun listProblems(
        @RequestParam(required = false) topic: String?,
        @RequestParam(required = false) difficulty: Int?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "12") size: Int,
        model: Model
    ): String {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "topic", "difficulty"))
        
        val problems = when {
            topic != null && difficulty != null -> 
                problemRepository.findByTopicAndDifficulty(topic, difficulty, pageable)
            topic != null -> 
                problemRepository.findByTopic(topic, pageable)
            difficulty != null -> 
                problemRepository.findByDifficulty(difficulty, pageable)
            else -> 
                problemRepository.findAll(pageable)
        }
        
        val topics = problemRepository.findDistinctTopics()
        
        model.addAttribute("problems", problems)
        model.addAttribute("topics", topics)
        model.addAttribute("selectedTopic", topic)
        model.addAttribute("selectedDifficulty", difficulty)
        model.addAttribute("problemCount", problemRepository.count())
        
        return "problems/list"
    }

    @GetMapping("/problems/{id}")
    fun problemDetail(@PathVariable id: UUID, model: Model): String {
        val problem = problemRepository.findById(id)
            .orElseThrow { NoSuchElementException("Problem not found with id: $id") }
        
        // Get related problems from the same topic
        val relatedProblems = problemRepository.findByTopic(
            problem.topic, 
            PageRequest.of(0, 6)
        ).content.filter { it.id != problem.id }.take(3)
        
        model.addAttribute("problem", problem)
        model.addAttribute("relatedProblems", relatedProblems)
        
        return "problems/detail"
    }
}
