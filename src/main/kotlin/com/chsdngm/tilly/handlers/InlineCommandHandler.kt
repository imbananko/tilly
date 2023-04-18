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
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

@Service
class InlineCommandHandler(
        val elasticsearchService: ElasticsearchService,
        val api: TelegramApi
) : AbstractHandler<InlineCommandUpdate>() {
    private val log = LoggerFactory.getLogger(javaClass)
    val chunkSize = 16

    //TODO remove
    @OptIn(ExperimentalTime::class)
    private val timeSource = TimeSource.Monotonic

    @OptIn(ExperimentalTime::class)
    override fun handleSync(update: InlineCommandUpdate) {
        if (update.value.isBlank() || update.value.length < 3) {
            return
        }

        val mark = timeSource.markNow()
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

        log.info("InlineCommandHandler.handleSync elapsed ${mark.elapsedNow()}")
        log.info("processed inline command update=$update")
    }

    override fun retrieveSubtype(update: Update) =
        if (update.hasInlineQuery()) InlineCommandUpdate(update) else null
}
