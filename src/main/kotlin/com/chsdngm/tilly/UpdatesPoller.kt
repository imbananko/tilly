package com.chsdngm.tilly

import com.chsdngm.tilly.config.MetadataProperties
import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.handlers.AbstractHandler
import com.chsdngm.tilly.model.Timestampable
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
    val api: TelegramApi,
    val metadata: MetadataProperties
) : TelegramLongPollingBot() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun getBotUsername(): String = telegramProperties.botUsername

    override fun getBotToken(): String = telegramProperties.botToken

    final override fun onUpdateReceived(update: Update) {
        val handlersIterator = handlers.iterator()
        var castedUpdate: Timestampable? = null
        var currentHandler: AbstractHandler<Timestampable>? = null
        while (handlersIterator.hasNext() && castedUpdate == null) {
            currentHandler = handlersIterator.next()
            castedUpdate = currentHandler.retrieveSubtype(update)
        }

        if (castedUpdate == null) {
            log.warn("No handler found for update=$update")
            return
        }

        currentHandler?.handle(castedUpdate)?.exceptionally {
            log.error("can't handle handle $update because of", it)

            SendMessage().apply {
                chatId = telegramProperties.logsChatId
                text = it.format(castedUpdate)
                parseMode = ParseMode.HTML
            }.let { method -> api.execute(method) }

            null
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
            text = "$botUsername started with sha: ${metadata.commitSha}"
            parseMode = ParseMode.HTML
        }.let { method -> executeAsync(method) }
    }
}
