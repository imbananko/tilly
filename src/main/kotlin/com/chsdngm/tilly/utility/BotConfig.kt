package com.chsdngm.tilly.utility

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

interface BotConfig {
  val chatId: Long
  val channelId: Long
  val betaChatId: Long
  val token: String
  val username: String

  fun getBotToken(): String
  fun getBotUsername(): String
  fun getBotPath(): String
}

@Component
class BotConfigImpl : BotConfig {
  @Value("\${target.chat.id}")
  override val chatId: Long = 0
  @Value("\${target.channel.id}")
  override val channelId: Long = 0
  @Value("\${beta.chat.id}")
  override val betaChatId: Long = 0
  @Value("\${bot.token}")
  override lateinit var token: String
  @Value("\${bot.username}")
  override lateinit var username: String

  override fun getBotToken(): String = token
  override fun getBotUsername(): String = username
  override fun getBotPath(): String = token
}