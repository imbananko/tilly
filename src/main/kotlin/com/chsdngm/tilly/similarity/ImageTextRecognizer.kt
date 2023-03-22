package com.chsdngm.tilly.similarity

import com.google.cloud.spring.vision.CloudVisionTemplate
import com.google.cloud.vision.v1.Feature.Type
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Service
import java.io.File


@Service
class ImageTextRecognizer(val cloudVisionTemplate: CloudVisionTemplate) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class AnalyzingResults(
        val words: String?,
        val labels: String?
    )

    fun analyze(image: File, fileId: String): AnalyzingResults? = runCatching {
            val response = cloudVisionTemplate.analyzeImage(
                ByteArrayResource(image.readBytes()),
                Type.TEXT_DETECTION,
                Type.LABEL_DETECTION
            )

            val words = response.textAnnotationsList.firstOrNull()?.description
            val labels = response.labelAnnotationsList.joinToString(separator = ",") { it.description }
            AnalyzingResults(words, labels)

        }.onSuccess {
            log.info("analyzing results=$it")
        }.onFailure {
            log.error("Failed to analyse with Google Vision API", it)
        }.getOrNull()
}