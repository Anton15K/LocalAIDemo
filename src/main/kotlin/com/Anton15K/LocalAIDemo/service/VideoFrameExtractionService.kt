package com.Anton15K.LocalAIDemo.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

@Service
class VideoFrameExtractionService {
    private val logger = LoggerFactory.getLogger(VideoFrameExtractionService::class.java)

    /**
     * Extracts at most maxFrames frames from video bytes.
     * Returns a map of timestamp (in ms) to the extracted frame file.
     */
    fun extractFrames(videoBytes: ByteArray, maxFrames: Int = 30): Pair<Map<Long, File>, File?> {
        val tempDir = Files.createTempDirectory("video_frames").toFile()
        val videoFile = File(tempDir, "input_video.mp4")
        videoFile.writeBytes(videoBytes)

        return try {
            // 1. Get video duration using ffprobe
            val durationSeconds = getVideoDuration(videoFile)
            logger.info("Video duration: $durationSeconds seconds")

            if (durationSeconds <= 0) {
                logger.warn("Could not determine video duration or duration is 0. Skipping frame extraction.")
                return Pair(emptyMap(), tempDir)
            }

            // 2. Calculate interval to get at most maxFrames
            val intervalSeconds = if (durationSeconds <= maxFrames) 1.0 else (durationSeconds / maxFrames)
            logger.info("Extracting frames with interval: $intervalSeconds seconds (maxFrames: $maxFrames)")

            val outputPattern = File(tempDir, "frame_%04d.jpg")

            // ffmpeg -i input.mp4 -vf "fps=1/interval" frame_%04d.jpg
            val process = try {
                ProcessBuilder(
                    "ffmpeg",
                    "-i", videoFile.absolutePath,
                    "-vf", "fps=1/$intervalSeconds",
                    outputPattern.absolutePath
                ).inheritIO().start()
            } catch (e: Exception) {
                logger.error("Failed to start ffmpeg process: ${e.message}. Please ensure ffmpeg is installed.")
                return Pair(emptyMap(), tempDir)
            }

            val exited = process.waitFor(5, TimeUnit.MINUTES)
            if (!exited) {
                process.destroyForcibly()
                logger.error("FFmpeg timed out during frame extraction")
                return Pair(emptyMap(), tempDir)
            }

            if (process.exitValue() != 0) {
                logger.error("FFmpeg failed with exit code ${process.exitValue()}")
                return Pair(emptyMap(), tempDir)
            }

            val frames = tempDir.listFiles { _, name -> name.startsWith("frame_") && name.endsWith(".jpg") }
                ?.sortedBy { it.name } ?: emptyList()

            // Limit to maxFrames if ffmpeg produced more due to rounding
            val limitedFrames = frames.take(maxFrames)

            val frameMap = limitedFrames.associateBy { file ->
                val frameNumber = file.name.removePrefix("frame_").removeSuffix(".jpg").toInt()
                // frame_0001 is at (frameNumber - 1) * intervalSeconds
                ( (frameNumber - 1) * intervalSeconds * 1000).toLong()
            }
            Pair(frameMap, tempDir)
        } catch (e: Exception) {
            logger.error("Unexpected error during frame extraction: ${e.message}", e)
            Pair(emptyMap(), tempDir)
        } finally {
            // We can delete the video file
            if (videoFile.exists()) {
                videoFile.delete()
            }
        }
    }

    private fun getVideoDuration(videoFile: File): Double {
        return try {
            val process = ProcessBuilder(
                "ffprobe",
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                videoFile.absolutePath
            ).start()

            val output = process.inputStream.bufferedReader().readText().trim()
            val exited = process.waitFor(10, TimeUnit.SECONDS)

            if (!exited) {
                process.destroyForcibly()
                logger.error("ffprobe timed out while getting video duration")
                0.0
            } else if (process.exitValue() != 0) {
                logger.error("ffprobe failed with exit code ${process.exitValue()}")
                0.0
            } else {
                output.toDoubleOrNull() ?: 0.0
            }
        } catch (e: Exception) {
            logger.error("Failed to run ffprobe: ${e.message}. Please ensure ffmpeg is installed.", e)
            0.0
        }
    }
}
