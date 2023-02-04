package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.InlineCommandUpdate
import com.chsdngm.tilly.similarity.ElasticsearchService
import kotlinx.coroutines.runBlocking
import org.apache.lucene.search.TotalHits
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.SearchHits
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

        val hitsList = (0..16).map { mock(SearchHit::class.java).apply { `when`(id).thenReturn("$it") } }.toTypedArray()
        val hits = SearchHits(hitsList, TotalHits(20, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO), 1.0f)
        //TODO shouldn't be runBlocking in tests???
        runBlocking {
            `when`(elasticsearchService.search("куда гонишь дед", 0, 16)).thenReturn(hits)
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
            verify(elasticsearchService).search("куда гонишь дед", 0, 16)
        }
        verify(api).execute(answerInlineMethod)
        verifyNoMoreInteractions(elasticsearchService)

    }
}