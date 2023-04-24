package com.chsdngm.tilly.handlers

import co.elastic.clients.elasticsearch.core.SearchResponse
import co.elastic.clients.elasticsearch.core.search.Hit
import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.model.InlineCommandUpdate
import com.chsdngm.tilly.similarity.ElasticsearchService
import com.chsdngm.tilly.similarity.ElasticsearchService.MemeDocument
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verifyBlocking
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.cached.InlineQueryResultCachedPhoto

class InlineCommandHandlerTest {
    private val elasticsearchService = mock<ElasticsearchService>()
    private val api = mock<TelegramApi>()
    private val inlineCommandHandler = InlineCommandHandler(elasticsearchService, api)

    @Test
    fun shouldDoNothingWhenNotEnoughSymbolsForSearching() {
        val update = mock<InlineCommandUpdate> {
            on(it.id).thenReturn("random_id")
            on(it.value).thenReturn("хи")
            on(it.offset).thenReturn("0")
        }

        inlineCommandHandler.handleSync(update)
        verifyNoMoreInteractions(elasticsearchService, api)
    }

    @Test
    fun shouldReturnFirstPageWhenQueryingWithManyResults() {
        val update = mock<InlineCommandUpdate> {
            on(it.id).thenReturn("random_id")
            on(it.value).thenReturn("куда гонишь дед")
            on(it.offset).thenReturn("0")
        }

        val hits = (0..16).map { number ->
            mock<Hit<MemeDocument>> {
                on(it.id()).thenReturn("$number")
            }
        }

        val response: SearchResponse<MemeDocument> = mock(defaultAnswer = RETURNS_DEEP_STUBS) {
            on(it.hits().hits()).thenReturn(hits)
        }

        elasticsearchService.stub {
            onBlocking { searchMemesByText("куда гонишь дед", 0, 16) }
                .thenReturn(response)
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
        verifyBlocking(elasticsearchService) { searchMemesByText("куда гонишь дед", 0, 16) }
        verify(api).execute(answerInlineMethod)
        verifyNoMoreInteractions(elasticsearchService, api)
    }
}