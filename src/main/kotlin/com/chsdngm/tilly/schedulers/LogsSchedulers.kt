package com.chsdngm.tilly.schedulers

import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.metrics.AccumulatingAppender
import com.chsdngm.tilly.similarity.ElasticsearchService
import com.chsdngm.tilly.utility.format
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.core.LogEvent
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage

@Service
final class LogsSchedulers(
    private val elasticsearchService: ElasticsearchService,
    private val api: TelegramApi,
    private val telegramProperties: TelegramProperties,
) {
    @Scheduled(cron = "0 * * * * *")
    fun sendLogs() = runBlocking {
        val logs = mutableListOf<LogEvent>()
        AccumulatingAppender.drain(logs)

        if (logs.isEmpty()) return@runBlocking

        runCatching {
            elasticsearchService.bulkIndexLogs(logs)
        }.onFailure {
            SendMessage().apply {
                chatId = telegramProperties.logsChatId
                text = it.format()
                parseMode = ParseMode.HTML
            }.let { method -> api.executeSuspended(method) }
        }
    }
}
