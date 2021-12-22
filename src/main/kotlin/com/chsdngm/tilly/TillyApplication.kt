package com.chsdngm.tilly

import com.chsdngm.tilly.utility.TillyConfig
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage

@SpringBootApplication
class TillyApplication

fun main(args: Array<String>) {
  SpringApplication.run(TillyApplication::class.java, *args)
  SendMessage().apply {
    chatId = TillyConfig.BETA_CHAT_ID
    text = "${TillyConfig.BOT_USERNAME} started"
    parseMode = ParseMode.HTML
  }.let { method -> TillyConfig.api.execute(method) }
}
