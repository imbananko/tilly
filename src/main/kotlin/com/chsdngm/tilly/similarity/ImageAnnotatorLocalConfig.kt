package com.chsdngm.tilly.similarity

import com.google.cloud.spring.vision.CloudVisionTemplate
import com.google.cloud.vision.v1.ImageAnnotatorClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
@ConditionalOnProperty(value = ["spring.cloud.gcp.vision.enabled"], havingValue = "false")
class ImageAnnotatorLocalConfig {

    @Bean
    @Primary
    fun imageAnnotatorClient(): ImageAnnotatorClient? {
        return null
    }

    @Bean
    @Primary
    fun cloudVisionTemplate(imageAnnotatorClient: ImageAnnotatorClient?): CloudVisionTemplate? {
        return null
    }
}