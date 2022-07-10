package com.chsdngm.tilly.controller

import com.chsdngm.tilly.model.AutosuggestionVoteValue
import com.chsdngm.tilly.utility.TillyConfig
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import java.util.Date


@RestController
class MemeController {
    @PostMapping(value = ["/memes/suggest"])
    fun index(@RequestParam file: MultipartFile, metadata: Metadata) {
        val markup = InlineKeyboardMarkup(
            listOf(
                listOf(InlineKeyboardButton("Отправить в предложку ${AutosuggestionVoteValue.APPROVE_SUGGESTION.emoji}").also { it.callbackData = AutosuggestionVoteValue.APPROVE_SUGGESTION.name }),
                listOf(InlineKeyboardButton("Предать забвению ${AutosuggestionVoteValue.DECLINE_SUGGESTION.emoji}").also { it.callbackData = AutosuggestionVoteValue.DECLINE_SUGGESTION.name })
            )
        )

        SendPhoto().apply {
            chatId = TillyConfig.BETA_CHAT_ID
            photo = InputFile(file.bytes.inputStream(), file.name)
            caption = "автопредложка:\n\nПаблик: ${metadata.communityName}\nСсылка на пост: ${metadata.url}\nВремя публикации: ${Date(metadata.postedAtTimestamp * 1000)}"
            disableNotification = true
            replyMarkup = markup
        }.let { TillyConfig.api.execute(it) }
    }
}

data class Metadata(
    val url: String,
    val communityName: String,
    val postedAtTimestamp: Long
)