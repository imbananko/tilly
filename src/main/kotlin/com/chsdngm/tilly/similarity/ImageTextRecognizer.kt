package com.chsdngm.tilly.similarity

import java.io.File
import com.google.cloud.vision.v1.Feature.Type
import org.slf4j.LoggerFactory
import org.springframework.cloud.gcp.vision.CloudVisionTemplate
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Service

@Service
class ImageTextRecognizer(val cloudVisionTemplate: CloudVisionTemplate) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun detectText(image: File): List<String> {
    cloudVisionTemplate.analyzeImage(ByteArrayResource(image.readBytes()), Type.TEXT_DETECTION).let {
      val words = it.textAnnotationsList.drop(1).map { word -> word.description }

      log.info("words=$words")
      return words
    }
  }
}