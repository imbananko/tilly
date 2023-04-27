package com.chsdngm.tilly

import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.metrics.AccumulatingAppender
import com.chsdngm.tilly.schedulers.LogsSchedulers
import com.chsdngm.tilly.similarity.ElasticsearchService
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.core.impl.Log4jLogEvent
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import java.lang.RuntimeException

class LogsSchedulerTest {
    private val api = mock<TelegramApi>()
    private val elasticsearchService = mock<ElasticsearchService>()

    private val telegramProperties = TelegramProperties(
        "montornChatId",
        "777",
        "targetChannelId",
        "botToken",
        "botUsername",
        "logsChatId",
        777
    )

    private val logsSchedulers =
        LogsSchedulers(elasticsearchService, api, telegramProperties)

    @Test
    fun shouldNotSendLogsWhenThereAreNoLogs() {
        logsSchedulers.sendLogs()

        verifyNoInteractions(elasticsearchService, api)
    }

    @Test
    fun shouldSendLogsWhenThereAreLogs() = runBlocking {
        AccumulatingAppender("test").append(Log4jLogEvent())

        logsSchedulers.sendLogs()

        verify(elasticsearchService, times(1)).bulkIndexLogs(any())
        verifyNoInteractions(api)
    }

    @Test
    fun shouldCallTelegramApiWhenThereIsException() = runBlocking<Unit> {
        AccumulatingAppender("test").append(Log4jLogEvent())
        whenever(elasticsearchService.bulkIndexLogs(any())).thenAnswer { throw RuntimeException("Bad Request") }

        logsSchedulers.sendLogs()

        verify(elasticsearchService, times(1)).bulkIndexLogs(any())
        verify(api, times(1)).executeSuspended(any<SendMessage>())
    }
}
