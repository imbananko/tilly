package com.chsdngm.tilly.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.bots.DefaultBotOptions

@Component
class TelegramConfig {
    companion object {

        @JvmField
        var CHAT_ID = ""

        @JvmField
        var CHANNEL_ID = ""

        @JvmField
        var BETA_CHAT_ID = ""

        @JvmField
        var MONTORN_CHAT_ID = ""

        @JvmField
        var BOT_TOKEN = ""

        @JvmField
        var BOT_USERNAME = ""

        @JvmField
        var BOT_ID = 0L

        @JvmField
        var LOGS_CHAT_ID = ""

        val api = object : DefaultAbsSender(
            DefaultBotOptions().apply {
                maxThreads = 8
                getUpdatesTimeout = 5
            }) {
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

    @Value("\${bot.id}")
    fun setBotId(id: Long) {
        BOT_ID = id
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

    @Value("\${logs.chat.id}")
    fun setLogsChatId(logsChatId: String) {
        LOGS_CHAT_ID = logsChatId
    }

    @Value("\${montorn.chat.id}")
    fun setMontornChatId(montornChatId: String) {
        MONTORN_CHAT_ID = montornChatId
    }

    @Value("\${elasticsearch.url}")
    lateinit var elasticsearchUrl: String
}
