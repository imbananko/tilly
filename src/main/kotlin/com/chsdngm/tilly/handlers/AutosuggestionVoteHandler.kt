package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.config.TelegramConfig
import com.chsdngm.tilly.model.AutoSuggestedMemeUpdate
import com.chsdngm.tilly.model.AutosuggestionVoteUpdate
import com.chsdngm.tilly.model.AutosuggestionVoteValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Service
class AutosuggestionVoteHandler(private val memeHandler: MemeHandler) :
        AbstractHandler<AutosuggestionVoteUpdate> {
    private val log = LoggerFactory.getLogger(javaClass)
    var executor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun handle(update: AutosuggestionVoteUpdate): CompletableFuture<Void> = CompletableFuture.supplyAsync({
        when (update.voteValue) {
            AutosuggestionVoteValue.APPROVE_SUGGESTION -> approve(update)
            AutosuggestionVoteValue.DECLINE_SUGGESTION -> decline(update)
        }
    },
            executor
    ).thenAccept { log.info("processed autosuggestion vote update=$update") }

    private fun approve(update: AutosuggestionVoteUpdate) {
        val memeUpdate = AutoSuggestedMemeUpdate(update)
        memeHandler.handle(memeUpdate)

        EditMessageCaption().apply {
            chatId = update.groupId.toString()
            messageId = update.messageId
            caption = "мем отправлен в общую предложку, если он не дубликат"
        }.let { TelegramConfig.api.execute(it) }
        log.info("autosuggested meme was approved by approver=${update.approver}")
    }

    private fun decline(update: AutosuggestionVoteUpdate) {
        EditMessageCaption().apply {
            chatId = update.groupId.toString()
            messageId = update.messageId
            caption = "мем предан забвению"
        }.let { TelegramConfig.api.execute(it) }

        log.info("autosuggested meme was declined by approver=${update.approver}")
    }
}