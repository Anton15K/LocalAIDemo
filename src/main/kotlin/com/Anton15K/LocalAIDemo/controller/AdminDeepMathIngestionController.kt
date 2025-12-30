package com.Anton15K.LocalAIDemo.controller

import com.Anton15K.LocalAIDemo.service.DeepMathIngestionJobService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/ingestion/deepmath")
class AdminDeepMathIngestionController(
    private val deepMathIngestionJobService: DeepMathIngestionJobService
) {

    @GetMapping("/status")
    fun status(): DeepMathIngestionJobService.DeepMathIngestionStatus {
        return deepMathIngestionJobService.getStatus()
    }

    @PostMapping("/start")
    fun start(
        @RequestParam(defaultValue = "zwhe99/DeepMath-103K") dataset: String,
        @RequestParam(defaultValue = "default") config: String,
        @RequestParam(defaultValue = "train") split: String,
        @RequestParam(defaultValue = "100") batchSize: Int,
        @RequestParam(defaultValue = "0") maxRows: Int,
        @RequestParam(defaultValue = "250") requestDelayMs: Long,
        @RequestParam(defaultValue = "false") indexEmbeddings: Boolean,
        @RequestParam(defaultValue = "100") indexChunkSize: Int,
        @RequestParam(defaultValue = "200") indexDelayMs: Long
    ): ResponseEntity<Map<String, Any?>> {
        val result = deepMathIngestionJobService.start(
            dataset = dataset,
            config = config,
            split = split,
            batchSize = batchSize,
            maxRows = maxRows,
            requestDelayMs = requestDelayMs,
            indexEmbeddings = indexEmbeddings,
            indexChunkSize = indexChunkSize,
            indexDelayMs = indexDelayMs
        )

        return if (result.started) {
            ResponseEntity.accepted().body(mapOf("status" to "started"))
        } else {
            ResponseEntity.status(409).body(mapOf("status" to "not_started", "reason" to result.reason))
        }
    }
}
