package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.config.TelegramConfig
import com.chsdngm.tilly.config.TelegramConfig.Companion.BETA_CHAT_ID
import com.chsdngm.tilly.model.ReelsLinkUpdate
import com.chsdngm.tilly.model.dto.Reel
import com.chsdngm.tilly.repository.ReelDao
import com.chsdngm.tilly.utility.format
import org.apache.commons.io.FileUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendVideo
import org.telegram.telegrambots.meta.api.objects.InputFile
import java.io.File
import java.lang.Exception
import java.net.URL

@Service
class ReelLinkHandler(
    private val api: DefaultAbsSender,
    private val reelDao: ReelDao) : AbstractHandler<ReelsLinkUpdate>() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val httpClient: CloseableHttpClient = HttpClientBuilder.create().build()

    override fun handleSync(update: ReelsLinkUpdate) {
        val reelDto = Reel(url = update.url)

        val innerUrl = try {
            reelDao.insert(reelDto)
            val url = fetchInnerUrl(update.url)
            reelDao.update(reelDto.copy(processed = true))

            url
        } catch (e: Exception) {
            log.error("failed to process reel url=${update.url}", e)
            SendMessage().apply {
                chatId = BETA_CHAT_ID
                text = e.format()
                parseMode = ParseMode.HTML
            }.let { method -> TelegramConfig.api.execute(method) }

            return
        }

        val file: File = File.createTempFile("video", "-innerUrl")
        file.deleteOnExit()

        FileUtils.copyURLToFile(URL(innerUrl), file)

        SendVideo().apply {
            chatId = BETA_CHAT_ID
            video = InputFile(file)
        }.let { method -> api.execute(method) }
    }

    private fun fetchInnerUrl(url: String): String {
        val shortUrl = url.split("?").dropLastWhile { it.isEmpty() }[0]

        val get = HttpGet("$shortUrl?__a=1&__d=dis").apply {
            addHeader("Accept", "application/json")
            addHeader("x-requested-with", "XMLHttpRequest")
            addHeader("Content-Type", "application/json;charset=UTF-8")
            addHeader(
                "user-agent",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.102 Safari/537.36"
            )
        }

        val response = String(httpClient.execute(get).entity.content.readAllBytes())

        val jsonObject = JSONObject(response)
        val objectGraphql = jsonObject.getJSONObject("graphql")
        val objectMedia = objectGraphql.getJSONObject("shortcode_media")
        val isVideo = objectMedia.getBoolean("is_video")
        return if (isVideo) {
            objectMedia.getString("video_url")
        } else {
            objectMedia.getString("display_url")
        }
    }
}
