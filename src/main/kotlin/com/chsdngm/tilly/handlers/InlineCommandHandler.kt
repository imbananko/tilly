package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.model.InlineCommandUpdate
import com.chsdngm.tilly.similarity.ElasticsearchService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.cached.InlineQueryResultCachedPhoto

@Service
class InlineCommandHandler(
        val elasticsearchService: ElasticsearchService,
        val api: TelegramApi
) : AbstractHandler<InlineCommandUpdate>() {
    private val log = LoggerFactory.getLogger(javaClass)
    val chunkSize = 16

    override fun handleSync(update: InlineCommandUpdate) {
        if (update.value.isBlank() || update.value.length < 3) {
            return
        }

        val offset = if (update.offset.isBlank()) 0 else update.offset.toInt()

        val cachedPhotos = runBlocking {
            elasticsearchService.searchMemesByText(update.value, offset, chunkSize).hits().hits().map {
                InlineQueryResultCachedPhoto().apply {
                    photoFileId = it.id()
                    id = it.id().take(64)
                }
            }
        }

        AnswerInlineQuery().apply {
            inlineQueryId = update.id
            nextOffset = "${offset + 1}"
            results = cachedPhotos
        }.let { api.execute(it) }

        log.info("processed inline command update=$update")
    }

    override fun retrieveSubtype(update: Update) =
        if (canHandle(update)) InlineCommandUpdate(update) else null

    override fun canHandle(update: Update): Boolean {
        return update.hasInlineQuery()
    }
}
