package com.chsdngm.tilly

import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.handlers.AbstractHandler
import com.chsdngm.tilly.model.Timestampable
import com.chsdngm.tilly.utility.formatExceptionForTelegramMessage
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import javax.annotation.PostConstruct

@Component
@ConditionalOnProperty(prefix = "telegram.polling", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class UpdatesPoller(
    val telegramProperties: TelegramProperties,
    val handlers: List<AbstractHandler<Timestampable>>,
    val api: TelegramApi
) : TelegramLongPollingBot() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun getBotUsername(): String = telegramProperties.botUsername

    override fun getBotToken(): String = telegramProperties.botToken

    final override fun onUpdateReceived(update: Update) {
        val handler = handlers.find { handler -> handler.canHandle(update) }
        if (handler == null) {
            log.warn("No handler found for update=$update")
            return
        }

        handler.retrieveSubtype(update)?.let { subType ->
            handler.handle(subType).exceptionally {
                log.error("can't handle handle $update because of", it)

                SendMessage().apply {
                    chatId = telegramProperties.logsChatId
                    text = it.format(subType)
                    parseMode = ParseMode.HTML
                }.let { method -> api.execute(method) }

                null
            }
        }
    }

    fun Throwable.format(update: Timestampable): String {
        val formatted = formatExceptionForTelegramMessage(this)

        return """
          |Exception: ${formatted.message}
          |
          |Cause: ${formatted.cause}
          |
          |Update: $update
          |
          |Stacktrace: 
          |${formatted.stackTrace}
  """.trimMargin()
    }

    @PostConstruct
    fun init() {
        SendMessage().apply {
            chatId = telegramProperties.logsChatId
            text = "$botUsername started with sha: ${com.chsdngm.tilly.config.Metadata.COMMIT_SHA}"
            parseMode = ParseMode.HTML
        }.let { method -> executeAsync(method) }
    }
}
