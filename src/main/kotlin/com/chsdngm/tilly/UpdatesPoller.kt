package com.chsdngm.tilly

import com.chsdngm.tilly.config.TelegramConfig
import com.chsdngm.tilly.config.TelegramConfig.Companion.BOT_TOKEN
import com.chsdngm.tilly.config.TelegramConfig.Companion.BOT_USERNAME
import com.chsdngm.tilly.config.TelegramConfig.Companion.api
import com.chsdngm.tilly.handlers.*
import com.chsdngm.tilly.model.*
import com.chsdngm.tilly.utility.*
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import java.util.concurrent.CompletableFuture
import javax.annotation.PostConstruct

@Component
@ConditionalOnProperty(prefix = "telegram.polling", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class UpdatesPoller(
    val memeHandler: MemeHandler,
    val voteHandler: VoteHandler,
    val commandHandler: CommandHandler,
    val inlineCommandHandler: InlineCommandHandler,
    val privateModerationVoteHandler: PrivateModerationVoteHandler,
    val autosuggestionVoteHandler: AutosuggestionVoteHandler,
    val distributedModerationVoteHandler: DistributedModerationVoteHandler,
    val metadata: com.chsdngm.tilly.config.Metadata,
    val telegramConfig: TelegramConfig,
) : TelegramLongPollingBot() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getBotUsername(): String = BOT_USERNAME

    override fun getBotToken(): String = BOT_TOKEN

    final override fun onUpdateReceived(update: Update) {
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
            }.let { method -> api.execute(method) }

            null
        }
    }

    @PostConstruct
    fun init() {
        SendMessage().apply {
            chatId = TelegramConfig.BETA_CHAT_ID
            text = "$botUsername started with sha: ${com.chsdngm.tilly.config.Metadata.COMMIT_SHA}"
            parseMode = ParseMode.HTML
        }.let { method -> executeAsync(method) }
    }
}


