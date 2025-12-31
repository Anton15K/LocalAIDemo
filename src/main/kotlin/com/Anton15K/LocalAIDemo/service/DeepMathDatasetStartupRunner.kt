package com.Anton15K.LocalAIDemo.service

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["app.ingestion.deepmath.enabled"], havingValue = "true")
class DeepMathDatasetStartupRunner(
    private val deepMathIngestionJobService: DeepMathIngestionJobService
) : ApplicationListener<ApplicationReadyEvent> {

    private val logger = LoggerFactory.getLogger(DeepMathDatasetStartupRunner::class.java)

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        Thread({
            try {
                val result = deepMathIngestionJobService.startFromConfiguredDefaults()
                if (!result.started) {
                    logger.info("DeepMath startup ingestion: not started (${result.reason ?: "unknown"})")
                }
            } catch (e: Exception) {
                logger.error("DeepMath ingestion crashed: ${e.message}", e)
            }
        }, "deepmath-ingestion").start()
    }
}
