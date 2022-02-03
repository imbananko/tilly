package com.chsdngm.tilly.utility

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.bots.DefaultBotOptions

@Component
class TillyConfig {
  companion object {

    @JvmField
    var CHAT_ID = ""

    @JvmField
    var CHANNEL_ID = ""

    @JvmField
    var BETA_CHAT_ID = ""

    @JvmField
    var BOT_TOKEN = ""

    @JvmField
    var MODERATION_THRESHOLD = 0L

    @JvmField
    var BOT_USERNAME = ""

    @JvmField
    var ELASTICSEARCH_URL = ""

    @JvmField
    var ELASTICSEARCH_INDEX_NAME = ""

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
  fun setBetaChatId(betaChatId: String) {
    BETA_CHAT_ID = betaChatId
  }

  @Value("\${target.channel.id}")
  fun setChannelId(channelId: String) {
    CHANNEL_ID = channelId
  }

  @Value("\${target.chat.id}")
  fun setChatId(chatId: String) {
    CHAT_ID = chatId
  }

  @Value("\${moderation.threshold}")
  fun setModerationThreshold(moderationThreshold: Long) {
    MODERATION_THRESHOLD = moderationThreshold
  }

  @Value("\${elasticsearch.url}")
  fun setElasticsearchUrl(elasticsearchUrl: String) {
    ELASTICSEARCH_URL = elasticsearchUrl
  }

  @Value("\${elasticsearch.index}")
  fun setElasticsearchIndexName(elasticsearchIndexName: String) {
    ELASTICSEARCH_INDEX_NAME = elasticsearchIndexName
  }
}
