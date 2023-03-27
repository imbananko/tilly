package com.chsdngm.tilly.similarity

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders
import co.elastic.clients.elasticsearch.core.BulkRequest
import co.elastic.clients.elasticsearch.core.BulkResponse
import co.elastic.clients.elasticsearch.core.IndexResponse
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.coroutines.future.await
import org.apache.logging.log4j.core.LogEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
//TODO add error handling
class ElasticsearchService(val asyncClient: ElasticsearchAsyncClient) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MEMES = "memes"
        private const val TILLY_LOGS = "tilly_logs"
        private const val RAW_TEXT = "raw_text"
    }

    suspend fun searchMemesByText(
        text: String,
        pageNumber: Int,
        pageSize: Int
    ): co.elastic.clients.elasticsearch.core.SearchResponse<MemeDocument> {
        return asyncClient.search(
            { request ->
                request
                    .index(MEMES)
                    .query(QueryBuilders.match { it.field(RAW_TEXT).query(text) })
                    .from(pageSize * pageNumber)
                    .size(pageSize)
            },
            MemeDocument::class.java
        ).await()
    }

    suspend fun indexMeme(id: String, meme: MemeDocument): IndexResponse {
        return asyncClient.index {
            it
                .index(MEMES)
                .id(id)
                .document(meme)
        }.thenApplyAsync {
            log.debug("Successfully indexed id=$id")
            it
        }.await()
    }

    suspend fun bulkIndexLogs(logEvents: List<LogEvent>): BulkResponse {
        val request = BulkRequest.Builder()

        for (event in logEvents) {
            request.operations {
                it
                    .index { idx: IndexOperation.Builder<LogDocument> ->
                        idx
                            .index(TILLY_LOGS)
                            .document(LogDocument(event))
                    }
            }
        }

        return asyncClient.bulk(request.build()).await()
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    data class MemeDocument(
        @get:JsonProperty("raw_text")
        @param:JsonProperty("raw_text")
        var rawText: String?,
        @get:JsonProperty("raw_labels")
        @param:JsonProperty("raw_labels")
        var rawLabels: String?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class LogDocument(
        var level: String,
        var logger: String,
        var thread: String,
        var timestamp: Long,
        var message: String,
    ) {
        constructor(event: LogEvent) : this(event.level.name(), event.loggerName, event.threadName, event.timeMillis, event.message.formattedMessage)
    }
}