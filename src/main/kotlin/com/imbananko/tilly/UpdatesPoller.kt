package com.imbananko.tilly

import com.imbananko.tilly.handlers.CommandHandler
import com.imbananko.tilly.handlers.MemeHandler
import com.imbananko.tilly.handlers.VoteHandler
import com.imbananko.tilly.model.CommandUpdate
import com.imbananko.tilly.model.MemeUpdate
import com.imbananko.tilly.model.VoteUpdate
import com.imbananko.tilly.utility.hasMeme
import com.imbananko.tilly.utility.hasStatsCommand
import com.imbananko.tilly.utility.hasVote
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class UpdatesPoller(val memeHandler: MemeHandler, val voteHandler: VoteHandler, val commandHandler: CommandHandler) : TelegramLongPollingBot() {

  @Value("\${bot.token}")
  protected lateinit var token: String

  @Value("\${bot.username}")
  protected lateinit var username: String

  final override fun getBotToken(): String? = token

  final override fun getBotUsername(): String? = username

  final override fun onUpdateReceived(update: Update) {
    when {
      update.hasVote() -> voteHandler.handle(VoteUpdate(update))
      update.hasMeme() -> memeHandler.handle(MemeUpdate(update))
      update.hasStatsCommand() -> commandHandler.handle(CommandUpdate(update))
    }
  }
}
