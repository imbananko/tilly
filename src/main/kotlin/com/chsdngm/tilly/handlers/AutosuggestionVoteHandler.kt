package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.model.AutosuggestionVoteUpdate
import com.chsdngm.tilly.model.AutosuggestionVoteValue
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption
import org.telegram.telegrambots.meta.api.objects.Update
import java.util.concurrent.Executors

@Service
class AutosuggestionVoteHandler(
    private val memeHandler: MemeHandler,
    private val api: TelegramApi,
    private val telegramProperties: TelegramProperties
) : AbstractHandler<AutosuggestionVoteUpdate>(Executors.newSingleThreadExecutor()) {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun handleSync(update: AutosuggestionVoteUpdate) = runBlocking {
        when (update.voteValue) {
            AutosuggestionVoteValue.APPROVE_SUGGESTION -> approve(update)
            AutosuggestionVoteValue.DECLINE_SUGGESTION -> decline(update)
        }

        log.info("processed autosuggestion vote update=$update")
    }

    private suspend fun approve(update: AutosuggestionVoteUpdate) {
        val memeUpdate = update.toAutoSuggestedMemeUpdate()
        memeHandler.handle(memeUpdate)

        EditMessageCaption().apply {
            chatId = update.chatId.toString()
            messageId = update.messageId
            caption = "мем отправлен в общую предложку, если он не дубликат"
        }.let { api.executeSuspended(it) }
        log.info("auto suggested meme was approved. update=$update")
    }

    private suspend fun decline(update: AutosuggestionVoteUpdate) {
        EditMessageCaption().apply {
            chatId = update.chatId.toString()
            messageId = update.messageId
            caption = "мем предан забвению"
        }.let { api.execute(it) }

        log.info("auto-suggested meme was declined. update=$update")
    }

    override fun retrieveSubtype(update: Update) =
        if (update.hasCallbackQuery()
            && update.callbackQuery.message.chatId.toString() == telegramProperties.montornChatId
            && AutosuggestionVoteValue.values().map { it.name }.contains(update.callbackQuery.data)
        ) {
            AutosuggestionVoteUpdate(update)
        } else null
}