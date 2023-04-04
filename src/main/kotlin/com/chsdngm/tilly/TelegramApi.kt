package com.chsdngm.tilly

import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.model.dto.Meme
import com.chsdngm.tilly.model.dto.Vote
import com.chsdngm.tilly.utility.createMarkup
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import java.io.File
import java.io.FileOutputStream
import java.io.Serializable
import java.net.URL
import java.util.concurrent.CompletableFuture

@Service
class TelegramApi(val properties: TelegramProperties) : DefaultAbsSender(
    DefaultBotOptions().apply {
        maxThreads = 8
        getUpdatesTimeout = 5
    }) {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getBotToken(): String = properties.botToken

    fun updateStatsInSenderChat(meme: Meme, votes: List<Vote>): CompletableFuture<Serializable?> =
        if (meme.privateReplyMessageId != null && meme.senderId != properties.botId) {
            val caption = meme.status.description +
                    votes
                        .groupingBy { it.value }
                        .eachCount().entries
                        .sortedBy { it.key }
                        .joinToString(
                            prefix = " статистика: \n\n",
                            transform = { (value, sum) -> "${value.emoji}: $sum" })

            EditMessageText().apply {
                chatId = meme.senderId.toString()
                messageId = meme.privateReplyMessageId
                text = caption
            }.let { executeAsync(it) }

        } else {
            CompletableFuture.completedFuture(null)
        }

    fun download(fileId: String): File {
        val file = File.createTempFile("photo-", "-" + Thread.currentThread().id + "-" + System.currentTimeMillis())
        file.deleteOnExit()

        FileOutputStream(file).use { out ->
            URL(execute(GetFile(fileId)).getFileUrl(properties.botToken)).openStream()
                .use { stream -> IOUtils.copy(stream, out) }
        }

        return file
    }

    fun updateChannelMarkup(meme: Meme, votes: List<Vote>) {
        try {
            EditMessageReplyMarkup().apply {
                chatId = properties.targetChannelId
                messageId = meme.channelMessageId
                replyMarkup = createMarkup(votes)
            }.let { execute(it) }
        } catch (e: Exception) {
            log.error("Failed to update markup. e=", e)
        }
    }
}