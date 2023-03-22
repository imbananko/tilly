package com.chsdngm.tilly

import com.chsdngm.tilly.config.TelegramConfig
import com.chsdngm.tilly.config.TelegramConfig.Companion.BOT_TOKEN
import com.chsdngm.tilly.config.TelegramConfig.Companion.BOT_USERNAME
import com.chsdngm.tilly.config.TelegramConfig.Companion.api
import com.chsdngm.tilly.handlers.*
import com.chsdngm.tilly.model.*
import com.chsdngm.tilly.utility.*
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import java.lang.reflect.UndeclaredThrowableException
import java.util.concurrent.CompletableFuture

@Component
@ConditionalOnMissingBean(UpdatesHooker::class)
class UpdatesPoller(
    val memeHandler: MemeHandler,
    val voteHandler: VoteHandler,
    val commandHandler: CommandHandler,
    val inlineCommandHandler: InlineCommandHandler,
    val privateModerationVoteHandler: PrivateModerationVoteHandler,
    val autosuggestionVoteHandler: AutosuggestionVoteHandler,
    val distributedModerationVoteHandler: DistributedModerationVoteHandler
) : TelegramLongPollingBot() {

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        log.info("UpdatesHooker init")
    }

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

    override fun onUpdatesReceived(updates: MutableList<Update>?) {
        super.onUpdatesReceived(updates)
    }
}

fun Throwable.format(update: Update?): String {
    val updateInfo = when {
        update == null -> "no update"
        update.hasVote() -> VoteUpdate(update).toString()
        update.hasMeme() -> UserMemeUpdate(update).toString()
        update.hasCommand() -> CommandUpdate(update).toString()
        update.hasPrivateVote() -> PrivateVoteUpdate(update).toString()
        else -> "unknown update=$update"
    }

    val exForBeta = when (this) {
        is UndeclaredThrowableException ->
            ExceptionForBeta(this.undeclaredThrowable.message, this, this.undeclaredThrowable.stackTrace)
        else ->
            ExceptionForBeta(this.message, this.cause, this.stackTrace)
    }

    return """
  |Exception: ${exForBeta.message}
  |
  |Cause: ${exForBeta.cause}
  |
  |Update: $updateInfo
  |
  |Stacktrace: 
  |${
        exForBeta.stackTrace.filter { it.className.contains("chsdngm") || it.className.contains("telegram") }
            .joinToString(separator = "\n\n") { "${it.className}.${it.methodName}:${it.lineNumber}" }
    }
  """.trimMargin()
}

private data class ExceptionForBeta(
    val message: String?,
    val cause: Throwable?,
    val stackTrace: Array<StackTraceElement>
)

