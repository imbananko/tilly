package com.imbananko.tilly

import com.imbananko.tilly.handlers.CommandHandler
import com.imbananko.tilly.handlers.MemeHandler
import com.imbananko.tilly.handlers.VoteHandler
import com.imbananko.tilly.model.CommandUpdate
import com.imbananko.tilly.model.MemeUpdate
import com.imbananko.tilly.model.VoteUpdate
import com.imbananko.tilly.utility.BotConfig
import com.imbananko.tilly.utility.hasMeme
import com.imbananko.tilly.utility.hasStatsCommand
import com.imbananko.tilly.utility.hasVote
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class UpdatesPoller(val memeHandler: MemeHandler,
                    val voteHandler: VoteHandler,
                    val commandHandler: CommandHandler,
                    val botConfig: BotConfig) : TelegramLongPollingBot(), BotConfig by botConfig {

  final override fun onUpdateReceived(update: Update) {
    when {
      update.hasVote() -> voteHandler.handle(VoteUpdate(update))
      update.hasMeme() -> memeHandler.handle(MemeUpdate(update))
      update.hasStatsCommand() -> commandHandler.handle(CommandUpdate(update))
    }
  }
}
