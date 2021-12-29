package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.InlineCommandUpdate
import com.chsdngm.tilly.similarity.ElasticsearchService
import com.chsdngm.tilly.utility.TillyConfig
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.cached.InlineQueryResultCachedPhoto

@Service
class InlineCommandHandler(val elasticsearchService: ElasticsearchService) : AbstractHandler<InlineCommandUpdate> {
    companion object {
        const val chunkSize = 16
    }

    override fun handle(update: InlineCommandUpdate) {
        if (update.value.isBlank() || update.value.length < 2) return

        val offset = if (update.offset.isBlank()) 0 else update.offset.toInt()

        val cachedPhotos = runBlocking {
            elasticsearchService.search(update.value, offset, chunkSize)
                .hits
                .map {
                    InlineQueryResultCachedPhoto().apply {
                        photoFileId = it.id
                        id = it.id.take(64)
                    }
                }
        }

        AnswerInlineQuery().apply {
            inlineQueryId = update.id
            nextOffset = "${offset + 1}"
            results = cachedPhotos
        }.let { TillyConfig.api.execute(it) }
    }
}