package com.chsdngm.tilly.utility

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.bots.DefaultBotOptions

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

    @JvmField
    var PAYMENT_URL = ""

    val api = object : DefaultAbsSender(DefaultBotOptions()) {
      override fun getBotToken(): String = BOT_TOKEN
    }

    var publishEnabled: Boolean = true
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

  @Value("\${payment.url}")
  fun setPaymentUrl(paymentUrl: String) {
    PAYMENT_URL = paymentUrl
  }

}