package com.chsdngm.tilly.handlers

import co.elastic.clients.elasticsearch.core.SearchResponse
import co.elastic.clients.elasticsearch.core.search.Hit
import co.elastic.clients.elasticsearch.core.search.HitsMetadata
import com.chsdngm.tilly.model.InlineCommandUpdate
import com.chsdngm.tilly.similarity.ElasticsearchService
import com.chsdngm.tilly.similarity.ElasticsearchService.MemeDocument
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.cached.InlineQueryResultCachedPhoto

class InlineCommandHandlerTest {
    private val elasticsearchService = mock(ElasticsearchService::class.java)
    private val api = mock(DefaultAbsSender::class.java)
    private val inlineCommandHandler = InlineCommandHandler(elasticsearchService, api)

    @Test
    fun shouldDoNothingWhenNotEnoughSymbolsForSearching() {
        val update = mock(InlineCommandUpdate::class.java).apply {
            `when`(id).thenReturn("random_id")
            `when`(value).thenReturn("хи")
            `when`(offset).thenReturn("0")
        }

        inlineCommandHandler.handleSync(update)
        verifyNoMoreInteractions(elasticsearchService)
    }

    @Test
    fun shouldReturnFirstPageWhenQueryingWithManyResults() {
        val update = mock(InlineCommandUpdate::class.java).apply {
            `when`(id).thenReturn("random_id")
            `when`(value).thenReturn("куда гонишь дед")
            `when`(offset).thenReturn("0")
        }

        val hits = (0..16).map {
            mock(Hit::class.java).apply {
                `when`(id()).thenReturn("$it")
            } as Hit<MemeDocument>
        }
        val hitsMetadata = mock(HitsMetadata::class.java) as HitsMetadata<MemeDocument>
        `when`(hitsMetadata.hits()).thenReturn(hits)

        val response = mock(SearchResponse::class.java) as SearchResponse<MemeDocument>
        `when`(response.hits()).thenReturn(hitsMetadata)

        //TODO shouldn't be runBlocking in tests???
        runBlocking {
            `when`(elasticsearchService.searchMemesByText("куда гонишь дед", 0, 16)).thenReturn(response)
        }

        val cachedPhotos = (0..16).map {
            InlineQueryResultCachedPhoto().apply {
                photoFileId = "$it"
                id = "$it".take(64)
            }
        }

        val answerInlineMethod = AnswerInlineQuery().apply {
            inlineQueryId = "random_id"
            nextOffset = "1"
            results = cachedPhotos
        }

        inlineCommandHandler.handleSync(update)
        runBlocking {
            verify(elasticsearchService).searchMemesByText("куда гонишь дед", 0, 16)
        }
        verify(api).execute(answerInlineMethod)
        verifyNoMoreInteractions(elasticsearchService)

    }
}