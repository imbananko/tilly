package com.chsdngm.tilly

import com.chsdngm.tilly.config.Metadata.Companion.COMMIT_SHA
import com.chsdngm.tilly.config.TelegramConfig
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage


@SpringBootApplication
class TillyApplication

fun main(args: Array<String>) {
    SpringApplication.run(TillyApplication::class.java, *args)
    sendStartMessageToBeta()
}

private fun sendStartMessageToBeta() = SendMessage().apply {
    chatId = TelegramConfig.BETA_CHAT_ID
    text = "${TelegramConfig.BOT_USERNAME} started with sha: $COMMIT_SHA"
    parseMode = ParseMode.HTML
}.let { method -> TelegramConfig.api.execute(method) }