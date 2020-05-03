package com.chsdngm.tilly

import com.chsdngm.tilly.handlers.CommandHandler
import com.chsdngm.tilly.handlers.MemeHandler
import com.chsdngm.tilly.handlers.VoteHandler
import com.chsdngm.tilly.model.CommandUpdate
import com.chsdngm.tilly.model.MemeUpdate
import com.chsdngm.tilly.model.VoteUpdate
import com.chsdngm.tilly.utility.*
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramWebhookBot
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.objects.Update
import java.io.Serializable

@Component
class UpdatesPoller(val memeHandler: MemeHandler,
                    val voteHandler: VoteHandler,
                    val commandHandler: CommandHandler,
                    val botConfig: BotConfigImpl) : TelegramWebhookBot(), BotConfig by botConfig {

  final override fun onWebhookUpdateReceived(update: Update): BotApiMethod<Serializable>? {
    when {
      update.hasVote() -> voteHandler.handle(VoteUpdate(update))
      update.hasMeme() -> memeHandler.handle(MemeUpdate(update))
      update.hasStatsCommand() -> commandHandler.handle(CommandUpdate(update))
    }
    return null
  }
}
