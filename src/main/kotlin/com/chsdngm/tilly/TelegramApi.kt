package com.chsdngm.tilly

import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.model.dto.InstagramReel
import com.chsdngm.tilly.model.dto.Meme
import com.chsdngm.tilly.model.dto.Vote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.send.SendVideo
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Message
import java.io.File
import java.io.FileOutputStream
import java.io.Serializable
import java.net.URL

@Service
class TelegramApi(val properties: TelegramProperties) : DefaultAbsSender(
    DefaultBotOptions().apply {
        maxThreads = 8
        getUpdatesTimeout = 5
    }) {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getBotToken(): String = properties.botToken

    suspend fun updateStatsInSenderChat(meme: Meme, votes: List<Vote>) {
        if (meme.privateReplyMessageId == null || meme.senderId == properties.botId) {
            return
        }

        val caption = meme.status.description +
                votes
                    .groupingBy { it.value }
                    .eachCount().entries
                    .sortedBy { it.key }
                    .joinToString(
                        prefix = " статистика: \n\n",
                        transform = { (value, sum) -> "${value.emoji}: $sum" })

        try {
            EditMessageText().apply {
                chatId = meme.senderId.toString()
                messageId = meme.privateReplyMessageId
                text = caption
            }.let { executeSuspended(it) }
        } catch (e: Exception) {
            log.error("Failed to update stats in sender chat", e)
        }
    }

    suspend fun updateStatsInSenderChat(reel: InstagramReel, votes: List<Vote>) {
        if (reel.privateReplyMessageId == null || reel.senderId == properties.botId) {
            return
        }

        val caption = reel.status.description +
                votes
                    .groupingBy { it.value }
                    .eachCount().entries
                    .sortedBy { it.key }
                    .joinToString(
                        prefix = " статистика: \n\n",
                        transform = { (value, sum) -> "${value.emoji}: $sum" })

        try {
            EditMessageText().apply {
                chatId = reel.senderId.toString()
                messageId = reel.privateReplyMessageId
                text = caption
            }.let { executeSuspended(it) }
        } catch (e: Exception) {
            log.error("Failed to update stats in sender chat", e)
        }
    }

    suspend fun download(fileId: String): File = withContext(Dispatchers.IO) {
        val file = File.createTempFile("photo-", "-" + Thread.currentThread().id + "-" + System.currentTimeMillis())
            .apply {
                deleteOnExit()
            }

        FileOutputStream(file).use { out ->
            URL(executeAsync(GetFile(fileId)).await().getFileUrl(properties.botToken)).openStream()
                .use { stream -> IOUtils.copy(stream, out) }
        }

        file
    }

    suspend fun <T : Serializable> executeSuspended(method: BotApiMethod<T>): T =
        sendApiMethodAsync(method).await()

    suspend fun executeSuspended(sendPhoto: SendPhoto): Message =
        executeAsync(sendPhoto).await()

    suspend fun executeSuspended(sendVideo: SendVideo): Message =
        executeAsync(sendVideo).await()

    suspend fun executeSuspended(sendMediaGroup: SendMediaGroup): MutableList<Message> =
        executeAsync(sendMediaGroup).await()
}