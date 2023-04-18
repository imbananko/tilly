package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.model.InlineCommandUpdate
import com.chsdngm.tilly.similarity.ElasticsearchService
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.cached.InlineQueryResultCachedPhoto
import java.util.concurrent.TimeUnit


@Service
class InlineCommandHandler(
    val elasticsearchService: ElasticsearchService,
    val api: TelegramApi
) : AbstractHandler<InlineCommandUpdate>() {
    companion object {
        const val cachePageSize = 20
        const val cacheSize = 200L
        const val cacheTtl = 10L
    }

    private val log = LoggerFactory.getLogger(javaClass)

    val cacheWriteMutex = Mutex()

    data class Key(
        val text: String,
        val pageNumber: Int //starts with 0
    )

    val cache: Cache<Key, List<InlineQueryResultCachedPhoto>> = CacheBuilder.newBuilder()
        .maximumSize(cacheSize)
        .expireAfterWrite(cacheTtl, TimeUnit.MINUTES)
        .recordStats()
        .build()

    override fun handleSync(update: InlineCommandUpdate) {
        if (update.value.isBlank() || update.value.length < 3) {
            return
        }

        val key = Key(update.value.lowercase(), if (update.offset.isBlank()) 0 else update.offset.toInt())

        val photos = cache.get(key) {
            runBlocking {
                var currentPageNumber = key.pageNumber
                val currentPageValues = mutableListOf<InlineQueryResultCachedPhoto>()

                val hits =
                    elasticsearchService.searchMemesByText(key.text, key.pageNumber * cachePageSize, 100).hits()
                        .hits()

                hits.forEachIndexed { index, it ->
                        currentPageValues.add(
                            InlineQueryResultCachedPhoto().apply {
                                photoFileId = it.id()
                                id = it.id().take(64)
                            })

                        if (currentPageValues.size == cachePageSize || index == hits.size - 1) {
                            cacheWriteMutex.withLock { cache.put(Key(key.text, currentPageNumber), currentPageValues.toList()) }
                            currentPageValues.clear()
                            currentPageNumber++
                        }
                    }

                cache.getIfPresent(key) ?: listOf()
            }
        }

        AnswerInlineQuery().apply {
            inlineQueryId = update.id
            nextOffset = "${key.pageNumber + 1}"
            results = photos
        }.let { api.execute(it) }

        log.info("processed inline command update=$update")
        log.info("stats: ${cache.stats()}")
    }

    override fun retrieveSubtype(update: Update) =
        if (update.hasInlineQuery()) InlineCommandUpdate(update) else null
}
