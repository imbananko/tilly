package com.chsdngm.tilly

import com.chsdngm.tilly.config.TelegramConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import org.telegram.telegrambots.updatesreceivers.DefaultWebhook

@Configuration
class Configuration {
    @Bean
    fun api(): DefaultAbsSender {
        return object : DefaultAbsSender(DefaultBotOptions()) {
            override fun getBotToken(): String = TelegramConfig.BOT_TOKEN
        }
    }

    @Bean
    fun webhook(telegramConfig: TelegramConfig): SetWebhook = SetWebhook(telegramConfig.webhookUrl)

    @Bean
    fun telegramBotsApi(): TelegramBotsApi =
            TelegramBotsApi(DefaultBotSession::class.java, DefaultWebhook().apply { setInternalUrl("http://127.0.0.1:8443") })
}