package com.chsdngm.tilly

import com.chsdngm.tilly.config.TelegramConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.bots.DefaultBotOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Configuration
class Configuration {
    @Bean
    fun forkJoinPool(): ExecutorService {
        return Executors.newWorkStealingPool()
    }

    @Bean
    fun api(): DefaultAbsSender {
        return object : DefaultAbsSender(DefaultBotOptions()) {
            override fun getBotToken(): String = TelegramConfig.BOT_TOKEN
        }
    }
}