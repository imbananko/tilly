package com.imbananko.tilly.handlers

import org.springframework.beans.factory.annotation.Value
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.meta.ApiContext

abstract class AbstractHandler<T: Any> : DefaultAbsSender(ApiContext.getInstance(DefaultBotOptions::class.java)) {
  @Value("\${target.chat.id}")
  protected val chatId: Long = 0

  @Value("\${target.channel.id}")
  protected val channelId: Long = 0

  @Value("\${bot.token}")
  protected lateinit var token: String

  override fun getBotToken(): String = token

  abstract fun handle(update: T)
}
