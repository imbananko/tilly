package com.chsdngm.tilly.similarity

import com.chsdngm.tilly.model.dto.*
import com.google.cloud.spring.vision.CloudVisionTemplate
import com.google.cloud.vision.v1.Feature.Type
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Service
import java.io.File


interface ImageTextRecognizer {
    fun analyze(image: File, fileId: String): ImageTextRecognizerGcp.AnalyzingResults?
}

@Service
@Profile("local")
class ImageTextRecognizerLocal: ImageTextRecognizer {
    override fun analyze(image: File, fileId: String): ImageTextRecognizerGcp.AnalyzingResults? {
        return null
    }
}

@Service
@Profile("default")
class ImageTextRecognizerGcp(
    val cloudVisionTemplate: CloudVisionTemplate,
): ImageTextRecognizer {
    private val log = LoggerFactory.getLogger(javaClass)

    data class AnalyzingResults(
        val words: String?,
        val labels: String?
    )

    override fun analyze(image: File, fileId: String): AnalyzingResults? = runCatching {
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