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
class UpdatesPoller(
  val memeHandler: MemeHandler,
  val voteHandler: VoteHandler,
  val commandHandler: CommandHandler,
  val privateModerationVoteHandler: PrivateModerationVoteHandler
) : TelegramLongPollingBot() {

  override fun getBotUsername(): String = BOT_USERNAME

  override fun getBotToken(): String = BOT_TOKEN

  final override fun onUpdateReceived(update: Update) {
    when {
      update.hasVote() -> voteHandler.handle(VoteUpdate(update))
      update.hasMeme() -> memeHandler.handle(MemeUpdate(update))
      update.hasCommand() -> commandHandler.handle(CommandUpdate(update))
      update.hasPrivateVote() -> privateModerationVoteHandler.handle(PrivateVoteUpdate(update))
    }
  }
}


