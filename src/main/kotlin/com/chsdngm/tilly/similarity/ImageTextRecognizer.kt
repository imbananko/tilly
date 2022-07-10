package com.chsdngm.tilly.similarity

import com.google.cloud.spring.vision.CloudVisionTemplate
import com.google.cloud.vision.v1.Feature.Type
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Service
import java.io.File


@Service
class ImageTextRecognizer(
    val cloudVisionTemplate: CloudVisionTemplate,
    val elasticsearchClient: RestHighLevelClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class AnalyzingResults(
        val words: List<String>,
        val labels: List<String>,
        val fileId: String,
    )

    companion object {
        private const val TEXT_FIELD_NAME = "text"
        private const val INDEX_DOCUMENT_TYPE = "_doc"
        private const val INDEX_NAME = "memes"
    }

    fun analyzeAndIndex(image: File, fileId: String): AnalyzingResults? {
        val results = runCatching {
            val response = cloudVisionTemplate.analyzeImage(
                ByteArrayResource(image.readBytes()),
                Type.TEXT_DETECTION,
                Type.LABEL_DETECTION
            )

            val words = response.textAnnotationsList.drop(1).map { word -> word.description }
            val labels = response.labelAnnotationsList.map { word -> word.description }
            AnalyzingResults(words, labels, fileId)

        }.onSuccess {
            log.info("analyzing results=$it")
        }.onFailure {
            log.error("Failed to analyse with Google Vision API", it)
        }.getOrNull()

        if (results?.words?.isNotEmpty() == true) {
            val text = results.words.joinToString(separator = " ")
            val indexRequest =
                IndexRequest(INDEX_NAME)
                    .id(results.fileId)
                    .type(INDEX_DOCUMENT_TYPE)
                    .source(TEXT_FIELD_NAME, text)

            runCatching {
                elasticsearchClient.index(indexRequest, RequestOptions.DEFAULT)
            }.onFailure {
                log.error("Failed to index to Elasticsearch", it)
            }
        }

        return results
    }
}