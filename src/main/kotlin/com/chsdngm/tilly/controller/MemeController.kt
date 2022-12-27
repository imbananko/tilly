package com.chsdngm.tilly.controller

import com.chsdngm.tilly.config.TelegramConfig
import com.chsdngm.tilly.model.AutosuggestionVoteValue
import com.chsdngm.tilly.similarity.ImageMatcher
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import java.awt.image.BufferedImage
import java.util.*
import javax.imageio.ImageIO


@RestController
class MemeController {
    @PostMapping(value = ["/memes/suggest"])
    fun suggest(@RequestParam file: MultipartFile, params: Params) {
        val markup = InlineKeyboardMarkup(
            listOf(
                listOf(InlineKeyboardButton("Отправить в предложку ${AutosuggestionVoteValue.APPROVE_SUGGESTION.emoji}").also {
                    it.callbackData = AutosuggestionVoteValue.APPROVE_SUGGESTION.name
                }),
                listOf(InlineKeyboardButton("Предать забвению ${AutosuggestionVoteValue.DECLINE_SUGGESTION.emoji}").also {
                    it.callbackData = AutosuggestionVoteValue.DECLINE_SUGGESTION.name
                })
            )
        )

        var inputFile: InputFile
        file.bytes.inputStream().use {
            inputFile = InputFile(it, file.name)
        }

        SendPhoto().apply {
            chatId = TelegramConfig.MONTORN_CHAT_ID
            photo = inputFile
            caption =
                "автопредложка:\n\nПаблик: ${params.communityName}\nСсылка на пост: ${params.url}\nВремя публикации: ${
                    Date(params.postedAtTimestamp * 1000)
                }"
            disableNotification = true
            replyMarkup = markup
        }.let { TelegramConfig.api.execute(it) }
    }

    data class Params(
        val url: String,
        val communityName: String,
        val postedAtTimestamp: Long
    )
}
