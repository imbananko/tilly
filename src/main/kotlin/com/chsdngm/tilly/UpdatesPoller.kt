package com.chsdngm.tilly

import com.chsdngm.tilly.handlers.CommandHandler
import com.chsdngm.tilly.handlers.MemeHandler
import com.chsdngm.tilly.handlers.PrivateModerationVoteHandler
import com.chsdngm.tilly.handlers.VoteHandler
import com.chsdngm.tilly.model.CommandUpdate
import com.chsdngm.tilly.model.MemeUpdate
import com.chsdngm.tilly.model.PrivateVoteUpdate
import com.chsdngm.tilly.model.VoteUpdate
import com.chsdngm.tilly.utility.*
import com.chsdngm.tilly.utility.TillyConfig.Companion.BOT_TOKEN
import com.chsdngm.tilly.utility.TillyConfig.Companion.BOT_USERNAME
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import java.lang.reflect.UndeclaredThrowableException

@Component
class UpdatesPoller(val memeHandler: MemeHandler,
                    val voteHandler: VoteHandler,
                    val commandHandler: CommandHandler,
                    val privateModerationVoteHandler: PrivateModerationVoteHandler) : TelegramLongPollingBot() {

  override fun getBotUsername(): String = BOT_USERNAME

  override fun getBotToken(): String = BOT_TOKEN

  final override fun onUpdateReceived(update: Update) {
    runCatching {
      when {
        update.hasVote() -> voteHandler.handle(VoteUpdate(update))
        update.hasMeme() -> memeHandler.handle(MemeUpdate(update))
        update.hasStatsCommand() -> commandHandler.handle(CommandUpdate(update))
        update.hasPrivateVote() -> privateModerationVoteHandler.handle(PrivateVoteUpdate(update))
      }
    }.onFailure {
      SendMessage()
          .setChatId(TillyConfig.BETA_CHAT_ID)
          .setText(it.format(update))
          .setParseMode(ParseMode.HTML)
          .apply { TillyConfig.api.execute(this) }
    }
  }
}

fun Throwable.format(update: Update?): String {
  val updateInfo = when {
    update == null -> "no update"
    update.hasVote() -> VoteUpdate(update).toString()
    update.hasMeme() -> MemeUpdate(update).toString()
    update.hasStatsCommand() -> CommandUpdate(update).toString()
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
  |${exForBeta.stackTrace.filter { it.className.contains("chsdngm") || it.className.contains("telegram") }.joinToString(separator = "\n") { "${it.className}.${it.methodName}:${it.lineNumber}" }}
  """.trimMargin()
}

private data class ExceptionForBeta(val message: String?, val cause: Throwable?, val stackTrace: Array<StackTraceElement>)

