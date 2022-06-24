package com.chsdngm.tilly.similarity

import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.SearchHits
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Service
class ElasticsearchService(val elasticsearchRestClient: RestHighLevelClient) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val INDEX_NAME = "memes"
        const val TEXT_FIELD = "text"
    }

    suspend fun search(text: String, pageNumber: Int, pageSize: Int): SearchHits {
        val searchRequest = SearchRequest(
            arrayOf(INDEX_NAME),
            SearchSourceBuilder
                .searchSource()
                .query(
                    QueryBuilders.matchQuery(TEXT_FIELD, text)
                ).from(pageSize * pageNumber)
                .size(pageSize)
        )

        return suspendCoroutine { continuation ->
            elasticsearchRestClient.searchAsync(
                searchRequest, RequestOptions.DEFAULT,
                object : ActionListener<SearchResponse> {

                    override fun onResponse(searchResponse: SearchResponse) {
                        log.info("Searched memes with text: ${text}, reached page: $pageNumber")
                        continuation.resume(searchResponse.hits)
                    }

                    override fun onFailure(e: Exception) {
                        log.error("Failed search with text: ${text}.", e)
                        continuation.resume(SearchHits.empty())
                    }
                })
        }
    }
}