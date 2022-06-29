package com.chsdngm.tilly

import com.chsdngm.tilly.handlers.*
import com.chsdngm.tilly.model.*
import com.chsdngm.tilly.utility.*
import com.chsdngm.tilly.utility.TillyConfig.Companion.BOT_TOKEN
import com.chsdngm.tilly.utility.TillyConfig.Companion.BOT_USERNAME
import com.chsdngm.tilly.utility.TillyConfig.Companion.api
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import java.lang.reflect.UndeclaredThrowableException

@Component
class UpdatesPoller(
    val memeHandler: MemeHandler,
    val voteHandler: VoteHandler,
    val commandHandler: CommandHandler,
    val inlineCommandHandler: InlineCommandHandler,
    val privateModerationVoteHandler: PrivateModerationVoteHandler
) : TelegramLongPollingBot() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getBotUsername(): String = BOT_USERNAME

    override fun getBotToken(): String = BOT_TOKEN

    final override fun onUpdateReceived(update: Update) {
        runCatching {
            when {
                update.hasVote() -> voteHandler.handle(VoteUpdate(update))
                update.hasMeme() -> memeHandler.handle(MemeUpdate(update))
                update.hasCommand() -> commandHandler.handle(CommandUpdate(update))
                update.hasPrivateVote() -> privateModerationVoteHandler.handle(PrivateVoteUpdate(update))
                update.hasInlineQuery() -> inlineCommandHandler.handle(InlineCommandUpdate(update))
            }
        }.onFailure {
            log.error("can't handle handle $update because of", it)

            SendMessage().apply {
                chatId = TillyConfig.BETA_CHAT_ID
                text = it.format(update)
                parseMode = ParseMode.HTML
            }.let { method -> api.execute(method) }
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
        update.hasMeme() -> MemeUpdate(update).toString()
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

