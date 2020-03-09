package com.chsdngm.tilly

import com.chsdngm.tilly.handlers.CommandHandler
import com.chsdngm.tilly.handlers.MemeHandler
import com.chsdngm.tilly.handlers.VoteHandler
import com.chsdngm.tilly.model.CommandUpdate
import com.chsdngm.tilly.model.MemeUpdate
import com.chsdngm.tilly.model.VoteUpdate
import com.chsdngm.tilly.utility.*
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class UpdatesPoller(val memeHandler: MemeHandler,
                    val voteHandler: VoteHandler,
                    val commandHandler: CommandHandler,
                    val botConfig: BotConfigImpl) : TelegramLongPollingBot(), BotConfig by botConfig {

  final override fun onUpdateReceived(update: Update) {
    when {
      update.hasVote() -> voteHandler.handle(VoteUpdate(update))
      update.hasMeme() -> memeHandler.handle(MemeUpdate(update))
      update.hasStatsCommand() -> commandHandler.handle(CommandUpdate(update))
    }
  }
}
