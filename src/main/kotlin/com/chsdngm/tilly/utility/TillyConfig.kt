package com.chsdngm.tilly.utility

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.bots.DefaultBotOptions
import java.time.Instant

@Component
class TillyConfig {
  companion object {

    @JvmField
    var CHAT_ID = 0L

    @JvmField
    var CHANNEL_ID = 0L

    @JvmField
    var BETA_CHAT_ID = 0L

    @JvmField
    var BOT_TOKEN = ""

    @JvmField
    var MODERATION_THRESHOLD = 0L

    @JvmField
    var BOT_USERNAME = ""

    val api = object : DefaultAbsSender(DefaultBotOptions()) {
      override fun getBotToken(): String = BOT_TOKEN
    }

    val CONTEST_END_DATETIME: Instant = Instant.parse("2020-08-26T19:00:00.000Z")
  }

  @Value("\${bot.token}")
  fun setToken(token: String) {
    BOT_TOKEN = token
  }

  @Value("\${bot.username}")
  fun setUsername(username: String) {
    BOT_USERNAME = username
  }

  @Value("\${beta.chat.id}")
  fun setBetaChatId(betaChatId: Long) {
    BETA_CHAT_ID = betaChatId
  }

  @Value("\${target.channel.id}")
  fun setChannelId(channelId: Long) {
    CHANNEL_ID = channelId
  }

  @Value("\${target.chat.id}")
  fun setChatId(chatId: Long) {
    CHAT_ID = chatId
  }

  @Value("\${moderation.threshold}")
  fun setModerationThreshold(moderationThreshold: Long) {
    MODERATION_THRESHOLD = moderationThreshold
  }

}