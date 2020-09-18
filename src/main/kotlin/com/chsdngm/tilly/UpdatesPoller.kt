package com.chsdngm.tilly

import com.chsdngm.tilly.handlers.AbstractHandler
import com.chsdngm.tilly.model.ConcreteUpdate
import com.chsdngm.tilly.utility.TillyConfig.Companion.BOT_TOKEN
import com.chsdngm.tilly.utility.TillyConfig.Companion.BOT_USERNAME
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.objects.Update

@Service
class UpdatesPoller(val handlers: List<AbstractHandler<out ConcreteUpdate>>) : TelegramLongPollingBot() {
  private val log = LoggerFactory.getLogger(javaClass)

  override fun getBotUsername(): String = BOT_USERNAME

  override fun getBotToken(): String = BOT_TOKEN

  final override fun onUpdateReceived(update: Update) {
    handlers.find { it.match(update) }?.handle(update) ?: log.error("Unrecognized update=$update")
  }
}
