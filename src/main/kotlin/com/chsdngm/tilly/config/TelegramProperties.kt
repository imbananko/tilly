package com.chsdngm.tilly.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding


@ConstructorBinding
@ConfigurationProperties(prefix = "telegram")
data class TelegramProperties(
    val montornChatId: String,
    val targetChatId: String,
    val targetChannelId: String,
    val botToken: String,
    val botUsername: String,
    val logsChatId: String,
    val botId: Long,
    var publishingEnabled: Boolean = true
)
