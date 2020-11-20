package com.chsdngm.tilly.similarity

import com.chsdngm.tilly.model.Image
import com.google.cloud.vision.v1.Feature.Type
import org.springframework.cloud.gcp.vision.CloudVisionTemplate
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Service

@Service
class GoogleImageRecognizer(val cloudVisionTemplate: CloudVisionTemplate) {
  fun enrich(image: Image): Image = cloudVisionTemplate.analyzeImage(ByteArrayResource(image.file), Type.LABEL_DETECTION, Type.TEXT_DETECTION).let {
    val words = it.textAnnotationsList.drop(1).map { word -> word.description }
    val labels = it.labelAnnotationsList.map { word -> word.description }

    return image.apply { this.labels = labels; this.words = words }
  }
}