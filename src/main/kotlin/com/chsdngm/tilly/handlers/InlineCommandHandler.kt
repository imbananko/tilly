package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.config.TelegramConfig
import com.chsdngm.tilly.model.InlineCommandUpdate
import com.chsdngm.tilly.similarity.ElasticsearchService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.cached.InlineQueryResultCachedPhoto
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Service
class InlineCommandHandler(val elasticsearchService: ElasticsearchService) : AbstractHandler<InlineCommandUpdate> {
    private val log = LoggerFactory.getLogger(javaClass)
    val chunkSize = 16
    var executor: ExecutorService = Executors.newFixedThreadPool(10)

    override fun handle(update: InlineCommandUpdate): CompletableFuture<Void> = CompletableFuture.supplyAsync({
        if (update.value.isBlank() || update.value.length < 2) return@supplyAsync

        val offset = if (update.offset.isBlank()) 0 else update.offset.toInt()

        val cachedPhotos = runBlocking {
            elasticsearchService.search(update.value, offset, chunkSize).hits.map {
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
        }.let { TelegramConfig.api.execute(it) }

    },
        executor
    ).thenAccept { log.info("processed inline command update=$update") }
}
