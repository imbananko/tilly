package com.chsdngm.tilly.metrics

import com.chsdngm.tilly.config.TelegramConfig
import com.chsdngm.tilly.config.TelegramConfig.Companion.LOGS_CHAT_ID
import com.chsdngm.tilly.config.TelegramConfig.Companion.api
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.layout.PatternLayout
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct


@Component
class TelegramLogAppender(val tillyConfig: TelegramConfig) :
    AbstractAppender("TelegramLogAppender", null, PatternLayout.createDefaultLayout(), false, arrayOf()) {

    val formatter = SimpleDateFormat("HH:mm:ss.SSS").apply { this.timeZone = TimeZone.getTimeZone("UTC") }

    private val logs = LinkedBlockingQueue<String>()
    private val executor = Executors.newScheduledThreadPool(1)

    init {
        executor.scheduleAtFixedRate(SendToChat(logs), 1, 1, TimeUnit.MINUTES)
    }

    class SendToChat(private val logs: LinkedBlockingQueue<String>) : Runnable {
        private val messageLimit = 4096

        override fun run() {
            val lines = mutableListOf<String>()
            logs.drainTo(lines)

            if (lines.isNotEmpty()) {

                val sb = StringBuilder()
                for (row in lines) {
                    if (sb.length + row.length < messageLimit) {
                        sb.appendLine("`$row`").append("\n")
                    } else {
                        SendMessage().apply {
                            chatId = LOGS_CHAT_ID
                            parseMode = ParseMode.MARKDOWN
                            text = sb.toString()
                        }.let { api.executeAsync(it) }

                        sb.clear()
                    }
                }

                if (sb.isNotEmpty()) {
                    SendMessage().apply {
                        chatId = LOGS_CHAT_ID
                        parseMode = ParseMode.MARKDOWN
                        text = sb.toString()
                    }.let { api.executeAsync(it) }
                }
            }
        }
    }

    override fun append(event: LogEvent) {
        val formattedMessage = "[${event.level.name()}] ${formatter.format(Date(event.instant.epochMillisecond))} [${event.threadName}] ${event.loggerName} - ${event.message.formattedMessage}"
        logs.add(formattedMessage)
    }

    @PostConstruct
    fun init() {
        val ctx = LoggerContext.getContext(false)

        this.start()

        ctx.configuration.getLoggerConfig("Exposed").addAppender(this, Level.DEBUG, null)
        ctx.configuration.getLoggerConfig("org.springframework.boot").addAppender(this, Level.ERROR, null)
        ctx.configuration.getLoggerConfig("root").addAppender(this, Level.ERROR, null)
        ctx.configuration.getLoggerConfig("com.chsdngm.tilly").addAppender(this, Level.INFO, null)
        ctx.updateLoggers()
    }
}
