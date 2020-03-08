package com.imbananko.tilly.utility

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

interface BotConfig {
  val chatId: Long
  val channelId: Long
  val token: String
  val username: String
  val votesForChannel: Int
  val allowVoteForYourself: Boolean

  fun getBotToken(): String
  fun getBotUsername(): String
}

@Component
class BotConfigImpl : BotConfig {
  @Value("\${target.chat.id}")
  override val chatId: Long = 0
  @Value("\${target.channel.id}")
  override val channelId: Long = 0
  @Value("\${bot.token}")
  override lateinit var token: String
  @Value("\${bot.username}")
  override lateinit var username: String
  @Value("\${bot.votes-for-channel:5}")
  override val votesForChannel: Int = 0
  @Value("\${bot.allow-vote-for-yourself:false}")
  override val allowVoteForYourself: Boolean = false

  override fun getBotToken(): String = token
  override fun getBotUsername(): String = username
}