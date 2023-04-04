package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.model.AutosuggestionVoteUpdate
import com.chsdngm.tilly.model.AutosuggestionVoteValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption
import java.util.concurrent.Executors

@Service
class AutosuggestionVoteHandler(
        private val memeHandler: MemeHandler,
        private val api: TelegramApi
)
    : AbstractHandler<AutosuggestionVoteUpdate>(Executors.newSingleThreadExecutor()) {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun handleSync(update: AutosuggestionVoteUpdate) {
        when (update.voteValue) {
            AutosuggestionVoteValue.APPROVE_SUGGESTION -> approve(update)
            AutosuggestionVoteValue.DECLINE_SUGGESTION -> decline(update)
        }

        log.info("processed autosuggestion vote update=$update")
    }

    private fun approve(update: AutosuggestionVoteUpdate) {
        val memeUpdate = update.toAutoSuggestedMemeUpdate()
        memeHandler.handle(memeUpdate)

        EditMessageCaption().apply {
            chatId = update.chatId.toString()
            messageId = update.messageId
            caption = "мем отправлен в общую предложку, если он не дубликат"
        }.let { api.execute(it) }
        log.info("auto suggested meme was approved. update=$update")
    }

    private fun decline(update: AutosuggestionVoteUpdate) {
        EditMessageCaption().apply {
            chatId = update.chatId.toString()
            messageId = update.messageId
            caption = "мем предан забвению"
        }.let { api.execute(it) }

        log.info("auto-suggested meme was declined. update=$update")
    }

    override fun getUpdateType() = AutosuggestionVoteUpdate::class
}