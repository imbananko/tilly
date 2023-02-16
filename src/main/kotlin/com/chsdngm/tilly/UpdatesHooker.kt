package com.chsdngm.tilly

import com.chsdngm.tilly.config.TelegramConfig
import com.chsdngm.tilly.handlers.*
import com.chsdngm.tilly.model.*
import com.chsdngm.tilly.utility.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updates.Close
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.starter.SpringWebhookBot
import java.util.concurrent.CompletableFuture

@Component
@ConditionalOnProperty(value = ["webhook.enabled"])
class UpdatesHooker(
        val memeHandler: MemeHandler,
        val voteHandler: VoteHandler,
        val commandHandler: CommandHandler,
        val inlineCommandHandler: InlineCommandHandler,
        val privateModerationVoteHandler: PrivateModerationVoteHandler,
        val autosuggestionVoteHandler: AutosuggestionVoteHandler,
        val distributedModerationVoteHandler: DistributedModerationVoteHandler,
        webhook: SetWebhook
) : SpringWebhookBot(webhook) {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    init {
        log.info("UpdatesHooker init")
    }

    override fun getBotUsername(): String = TelegramConfig.BOT_USERNAME

    override fun onWebhookUpdateReceived(update: Update): BotApiMethod<*> {
        log.info("received webhook update")
        when {
            update.hasVote() -> voteHandler.handle(VoteUpdate(update))
            update.hasMeme() -> memeHandler.handle(UserMemeUpdate(update))
            update.hasCommand() -> commandHandler.handle(CommandUpdate(update))
            update.hasPrivateVote() -> privateModerationVoteHandler.handle(PrivateVoteUpdate(update))
            update.hasAutosuggestionVote() -> autosuggestionVoteHandler.handle(AutosuggestionVoteUpdate(update))
            update.hasDistributedModerationVote() -> distributedModerationVoteHandler.handle(DistributedModerationVoteUpdate(update))
            update.hasInlineQuery() -> inlineCommandHandler.handle(InlineCommandUpdate(update))
            else -> CompletableFuture.completedFuture(null)

        }.exceptionally {
            log.error("can't handle handle $update because of", it)

            SendMessage().apply {
                chatId = TelegramConfig.BETA_CHAT_ID
                text = it.format(update)
                parseMode = ParseMode.HTML
            }.let { method -> TelegramConfig.api.execute(method) }

            null
        }

        return Close()
    }

    override fun getBotToken(): String = TelegramConfig.BOT_TOKEN

    override fun getBotPath() = BOT_PATH

    companion object {
        const val BOT_PATH: String = "tilly"
    }
}