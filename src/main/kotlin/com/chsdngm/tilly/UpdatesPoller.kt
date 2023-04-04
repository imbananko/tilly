package com.chsdngm.tilly

import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.handlers.*
import com.chsdngm.tilly.model.*
import com.chsdngm.tilly.utility.formatExceptionForTelegramMessage
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
    val telegramProperties: TelegramProperties,
    val api: TelegramApi
) : TelegramLongPollingBot() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun getBotUsername(): String = telegramProperties.botUsername

    override fun getBotToken(): String = telegramProperties.botToken

    final override fun onUpdateReceived(update: Update) {
        when {
            update.hasVote() -> voteHandler.handle(VoteUpdate(update))
            update.hasMeme() -> memeHandler.handle(UserMemeUpdate(update))
            update.hasCommand() -> commandHandler.handle(CommandUpdate(update))
            update.hasPrivateVote() -> privateModerationVoteHandler.handle(PrivateVoteUpdate(update))
            update.hasAutosuggestionVote(telegramProperties) -> autosuggestionVoteHandler.handle(
                AutosuggestionVoteUpdate(update)
            )

            update.hasDistributedModerationVote() -> distributedModerationVoteHandler.handle(
                DistributedModerationVoteUpdate(update)
            )

            update.hasInlineQuery() -> inlineCommandHandler.handle(InlineCommandUpdate(update))
            else -> CompletableFuture.completedFuture(null)

        }.exceptionally {
            log.error("can't handle handle $update because of", it)

            SendMessage().apply {
                chatId = telegramProperties.betaChatId
                text = it.format(update)
                parseMode = ParseMode.HTML
            }.let { method -> api.execute(method) }

            null
        }
    }

    fun Update.hasMeme() = this.hasMessage() && this.message.chat.isUserChat && this.message.hasPhoto()

    fun Update.hasCommand() = this.hasMessage() &&
            (this.message.chat.isUserChat || this.message.chatId.toString() == telegramProperties.betaChatId) &&
            this.message.isCommand

    fun Update.hasVote() = this.hasCallbackQuery()
            && (this.callbackQuery.message.isSuperGroupMessage || this.callbackQuery.message.isChannelMessage)
            && runCatching {
        setOf(*VoteValue.values()).map { it.name }.contains(this.callbackQuery.data)
    }.getOrDefault(false)

    fun Update.hasPrivateVote() = this.hasCallbackQuery()
            && this.callbackQuery.message.chat.isUserChat
            && runCatching {
        setOf(*PrivateVoteValue.values()).map { it.name }.contains(this.callbackQuery.data)
    }.getOrDefault(false)

    fun Update.hasDistributedModerationVote() = this.hasCallbackQuery()
            && this.callbackQuery.message.chat.isUserChat
            && runCatching {
        setOf(*DistributedModerationVoteValue.values()).map { it.name }.contains(this.callbackQuery.data)
    }.getOrDefault(false)

    fun Update.hasAutosuggestionVote(telegramProperties: TelegramProperties) = this.hasCallbackQuery()
            && this.callbackQuery.message.chatId.toString() == telegramProperties.montornChatId
            && runCatching {
        setOf(*AutosuggestionVoteValue.values()).map { it.name }.contains(this.callbackQuery.data)
    }.getOrDefault(false)

    fun Throwable.format(update: Update): String {
        val updateInfo = when {
            update.hasVote() -> "${VoteUpdate(update)}"
            update.hasMeme() -> "${UserMemeUpdate(update)}"
            update.hasCommand() -> "${CommandUpdate(update)}"
            update.hasPrivateVote() -> "${PrivateVoteUpdate(update)}"
            update.hasAutosuggestionVote(telegramProperties) -> "${AutosuggestionVoteUpdate(update)}"
            update.hasDistributedModerationVote() -> "${DistributedModerationVoteUpdate(update)}"
            update.hasInlineQuery() -> "${InlineCommandUpdate(update)}"
            else -> "unknown update=$update"
        }

        val formatted = formatExceptionForTelegramMessage(this)

        return """
          |Exception: ${formatted.message}
          |
          |Cause: ${formatted.cause}
          |
          |Update: $updateInfo
          |
          |Stacktrace: 
          |${formatted.stackTrace}
  """.trimMargin()
    }

    @PostConstruct
    fun init() {
        log.info("UpdatesPoller init")

        SendMessage().apply {
            chatId = telegramProperties.betaChatId
            text = "$botUsername started with sha: ${com.chsdngm.tilly.config.Metadata.COMMIT_SHA}"
            parseMode = ParseMode.HTML
        }.let { method -> executeAsync(method) }
    }
}
