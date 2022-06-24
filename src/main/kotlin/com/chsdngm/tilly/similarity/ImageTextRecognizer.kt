package com.chsdngm.tilly.similarity

import java.io.File
import com.google.cloud.vision.v1.Feature.Type
import org.slf4j.LoggerFactory
import org.springframework.cloud.gcp.vision.CloudVisionTemplate
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Service

data class AnalyzingResults(val words: List<String>,
                            val labels: List<String>)

@Service
class ImageTextRecognizer(val cloudVisionTemplate: CloudVisionTemplate) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun analyze(image: File): AnalyzingResults {
    cloudVisionTemplate.analyzeImage(ByteArrayResource(image.readBytes()), Type.TEXT_DETECTION, Type.LABEL_DETECTION).let {
      val words = it.textAnnotationsList.drop(1).map { word -> word.description }
      val labels = it.labelAnnotationsList.map { word -> word.description }
      val results = AnalyzingResults(words, labels)

      log.info("analyzing results=$results")

      return results
    }
  }
}