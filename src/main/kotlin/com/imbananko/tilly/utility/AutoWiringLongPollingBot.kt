package com.imbananko.tilly.utility

import org.springframework.context.annotation.Bean
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.Message

abstract class AutoWiringLongPollingBot : TelegramLongPollingBot() {
    @Bean
    fun sendMessage(): Function1<SendMessage, Message> = { sendMessage ->
        super.execute(sendMessage)
    }

    @Bean
    fun sendPhoto(): Function1<SendPhoto, Message> = { sendPhoto ->
        super.execute(sendPhoto)
    }
}