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
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
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

        val reelEntity = instagramReelDao.insert(
            InstagramReel(
                url = update.url,
                status = InstagramReelStatus.PROCESSING,
                senderId = update.user.id,
                created = Instant.ofEpochMilli(update.createdAt)
            )
        )

        val replyMessageId = replyToSender(update).messageId

        val innerUrl = try {
            fetchInnerUrl(update.postId)
        } catch (e: Exception) {
            log.error("failed to process reel url=${update.url}", e)
            SendMessage().apply {
                chatId = properties.logsChatId
                text = e.format()
                parseMode = ParseMode.HTML
            }.let { method -> api.execute(method) }

            instagramReelDao.update(reelEntity.apply { status = InstagramReelStatus.FAILED })

            return
        }

        updateReelStatusToModeration(update.user.id.toString(), replyMessageId)

        val fileId = sendVideoToUser(innerUrl, update.user.id).video?.fileId
        val messageInModerationChat = moderateInstagramReelVideo(fileId, update, isFreshman)

        instagramReelDao.update(
            reelEntity.copy(
                status = InstagramReelStatus.MODERATION,
                moderationChatId = messageInModerationChat.chatId,
                moderationChatMessageId = messageInModerationChat.messageId,
                privateReplyMessageId = replyMessageId,
                fileId = fileId,
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

    private fun sendVideoToUser(innerUrl: String, userId: Long): Message {
        val file = File.createTempFile("video", "-$innerUrl").apply {
            deleteOnExit()
        }

        FileUtils.copyURLToFile(URL(innerUrl), file)

        return SendVideo().apply {
            chatId = userId.toString()
            video = InputFile(file)
            parseMode = ParseMode.HTML
            caption = """<a href="https://t.me/chsdngm">че с деньгами</a>"""
        }.let { api.execute(it) }
    }

    private fun moderateInstagramReelVideo(
        fileId: String?,
        update: InstagramReelsLinkUpdate,
        isFreshman: Boolean
    ) = SendVideo().apply {
        chatId = properties.targetChatId
        video = InputFile(fileId)
        replyMarkup = createMarkup()
        caption = resolveCaption(update) + if (isFreshman) "\n\n#freshman" else ""
    }.let { api.execute(it) }

    private fun fetchInnerUrl(postId: String): String {
        val uri = URIBuilder("https://www.instagram.com/graphql/query")
            .addParameter("query_hash", "2b0673e0dc4580674a88d426fe00ea90")
            .addParameter("variables", "{\"shortcode\":\"$postId\"}")
            .build()

        log.debug("Requesting instagram: {}", uri)
        val execute = httpClient.execute(HttpGet(uri))
        val response = String(execute.entity.content.readAllBytes())
        log.debug("Response: {}", response)

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

    private fun updateReelStatusToModeration(userId: String, replyMessageId: Int) {
        EditMessageText().apply {
            chatId = userId
            messageId = replyMessageId
            text = "рилс на модерации"
        }.let { api.execute(it) }
    }

    private fun replyToSender(update: InstagramReelsLinkUpdate): Message =
        SendMessage().apply {
            chatId = update.user.id.toString()
            replyToMessageId = update.messageId
            disableNotification = true
            text = "рилс на обработке"
        }.let { api.execute(it) }

    override fun retrieveSubtype(update: Update): InstagramReelsLinkUpdate? {
        if (update.message?.chat?.isUserChat != true) return null
        if (!update.message.hasText()) return null

        val postId = Regex("reel.?/([^/]+)").find(update.message.text)?.groupValues?.last() ?: return null

        return InstagramReelsLinkUpdate(
            url = update.message.text,
            postId = postId,
            user = update.message.from,
            messageId = update.message.messageId
        )
    }
}
