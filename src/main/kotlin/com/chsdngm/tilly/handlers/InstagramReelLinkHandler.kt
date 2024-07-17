package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.*
import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.model.InstagramReelStatus
import com.chsdngm.tilly.model.InstagramReelsLinkUpdate
import com.chsdngm.tilly.model.dto.InstagramReel
import com.chsdngm.tilly.model.dto.TelegramUser
import com.chsdngm.tilly.repository.InstagramReelDao
import com.chsdngm.tilly.repository.TelegramUserDao
import org.apache.commons.io.FileUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendVideo
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import java.io.File
import java.net.URL
import java.time.Instant


@Service
class InstagramReelLinkHandler(
    private val api: TelegramApi,
    private val instagramReelDao: InstagramReelDao,
    private val properties: TelegramProperties,
    private val telegramUserDao: TelegramUserDao,
) : AbstractHandler<InstagramReelsLinkUpdate>() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val httpClient: CloseableHttpClient = HttpClientBuilder.create().build()

    override fun handleSync(update: InstagramReelsLinkUpdate) {
        var isFreshman = false

        val foundUser = telegramUserDao.findById(update.user.id)
        if (foundUser == null) {
            isFreshman = true

            telegramUserDao.insert(
                TelegramUser(
                    update.user.id,
                    update.user.userName,
                    update.user.firstName,
                    update.user.lastName
                )
            )
        } else {
            val updatedUser = foundUser.copy(
                username = update.user.userName,
                firstName = update.user.firstName,
                lastName = update.user.lastName
            )

            if (foundUser != updatedUser) {
                telegramUserDao.update(updatedUser)
            }
        }

        val entity = instagramReelDao.insert(InstagramReel(
            url = update.url,
            status = InstagramReelStatus.PROCESSING,
            senderId = update.user.id,
            created = Instant.ofEpochMilli(update.createdAt)
        ))

        val innerUrl = try {
            fetchInnerUrl(update.postId)
        } catch (e: Exception) {
            log.error("failed to process reel url=${update.url}", e)
            SendMessage().apply {
                chatId = properties.logsChatId
                text = e.format()
                parseMode = ParseMode.HTML
            }.let { method -> api.execute(method) }

            instagramReelDao.update(entity.apply { status = InstagramReelStatus.FAILED })

            return
        }

        val message = moderateInstagramReelVideo(innerUrl, update, isFreshman)
        val replyMessageId = replyToSender(update).messageId

        instagramReelDao.update(
            entity.copy(
                status = InstagramReelStatus.MODERATION,
                moderationChatId = message.chatId,
                moderationChatMessageId = message.messageId,
                privateReplyMessageId = replyMessageId,
                fileId = message.video?.fileId,
            )
        )
    }

    private fun resolveCaption(update: InstagramReelsLinkUpdate): String {
        runCatching {
            GetChatMember().apply {
                chatId = properties.targetChatId
                userId = update.user.id
            }.let(api::execute)
        }.onSuccess {
            if (!it.isFromChat()) {
                return "Sender: ${it.user.mention()}"
            }
        }

        return ""
    }

    private fun moderateInstagramReelVideo(innerUrl: String, update: InstagramReelsLinkUpdate, isFreshman: Boolean): Message {
        val file = File.createTempFile("video", "-$innerUrl").apply {
            deleteOnExit()
        }

        FileUtils.copyURLToFile(URL(innerUrl), file)

        return SendVideo().apply {
            chatId = properties.targetChatId
            video = InputFile(file)
            replyMarkup = createMarkup()
            caption = resolveCaption(update) + if (isFreshman) "\n\n#freshman" else ""
        }.let { api.execute(it) }
    }

    private fun fetchInnerUrl(postId: String): String {
        val uri = URIBuilder("https://www.instagram.com/graphql/query")
            .addParameter("query_hash", "2b0673e0dc4580674a88d426fe00ea90")
            .addParameter("variables", "{\"shortcode\":\"$postId\"}")
            .build()

        val execute = httpClient.execute(HttpGet(uri))
        val response = String(execute.entity.content.readAllBytes())

        val jsonObject = JSONObject(response)
        val objectGraphql = jsonObject.getJSONObject("data")
        val objectMedia = objectGraphql.getJSONObject("shortcode_media")
        val isVideo = objectMedia.getBoolean("is_video")

        return if (isVideo) {
            objectMedia.getString("video_url")
        } else {
            objectMedia.getString("display_url")
        }
    }

    private fun replyToSender(update: InstagramReelsLinkUpdate): Message = SendMessage().apply {
        chatId = update.user.id.toString()
        replyToMessageId = update.messageId
        disableNotification = true
        text = "рилс на модерации"
    }.let { api.execute(it) }

    override fun retrieveSubtype(update: Update): InstagramReelsLinkUpdate? {
        if (update.message?.chat?.isUserChat != true) return null
        if (!update.message.hasText()) return null

        val postId = Regex("reel.?\\/([^\\/]+)").find(update.message.text)?.groupValues?.last() ?: return null

        return InstagramReelsLinkUpdate(
            url = update.message.text,
            postId = postId,
            user = update.message.from,
            messageId = update.message.messageId
        )
    }
}
