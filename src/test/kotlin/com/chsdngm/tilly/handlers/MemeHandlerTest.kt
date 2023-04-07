package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.metrics.MetricsUtils
import com.chsdngm.tilly.model.MemeUpdate
import com.chsdngm.tilly.model.UserStatus
import com.chsdngm.tilly.model.dto.TelegramUser
import com.chsdngm.tilly.repository.DistributedModerationEventDao
import com.chsdngm.tilly.repository.ImageDao
import com.chsdngm.tilly.repository.MemeDao
import com.chsdngm.tilly.repository.TelegramUserDao
import com.chsdngm.tilly.similarity.ElasticsearchService
import com.chsdngm.tilly.similarity.ImageMatcher
import com.chsdngm.tilly.similarity.ImageTextRecognizer
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import java.io.File


//TODO: work in progress...
class MemeHandlerTest {
    private val telegramUserDao = mock<TelegramUserDao>()
    private val imageMatcher = mock<ImageMatcher>()
    private val imageTextRecognizer = mock<ImageTextRecognizer>()
    private val imageDao = mock<ImageDao>()
    private val memeDao = mock<MemeDao>()
    private val distributedModerationEventDao = mock<DistributedModerationEventDao>()
    private val metricsUtils = mock<MetricsUtils>()
    private val api = mock<TelegramApi>()
    private val elasticsearchService = mock<ElasticsearchService>()

    private val telegramProperties = TelegramProperties(
        "montornChatId",
        "777",
        "targetChannelId",
        "botToken",
        "botUsername",
        "logsChatId",
        777
    )

    private val memeHandler = MemeHandler(
        telegramUserDao,
        imageMatcher,
        imageTextRecognizer,
        imageDao,
        memeDao,
        distributedModerationEventDao,
        metricsUtils,
        api,
        elasticsearchService,
        telegramProperties
    )

    @Test
    fun shouldHandleFreshmanWhenUserIsNew() {
        //TODO
    }

    @Test
    fun shouldUpdateUserInfoWhenUserSendsMeme() {
        val update: MemeUpdate = mock(defaultAnswer = RETURNS_DEEP_STUBS) {
            on(it.user.id).thenReturn(111)
            on(it.fileId).thenReturn("random_file_id")
            on(it.user.userName).thenReturn("new_name")
        }

        whenever(api.execute(any<SendMessage>())).thenReturn(mock())
        val file = File.createTempFile("photo-", "${Thread.currentThread().id}")
        file.deleteOnExit()
        whenever(api.download("random_file_id")).thenReturn(file)
        whenever(imageMatcher.calculateHash(file)).thenReturn(ByteArray(1))

        val message = mock<Message> { on(it.messageId).thenReturn(0) }
        whenever(api.execute(any<SendPhoto>())).thenReturn(message)
        whenever(api.executeAsync(any<SendPhoto>())).thenReturn(mock())
        whenever(telegramUserDao.findById(111)).thenReturn(
            TelegramUser(
                111,
                "old_name",
                null,
                null
            )
        )

        memeHandler.handleSync(update)
        verify(telegramUserDao).findById(111)
        verify(telegramUserDao).update(
            TelegramUser(
                111,
                "new_name",
                null,
                null
            )
        )
    }

    @Test
    fun shouldHandleMemeWhenSendByBannedUser() {
        val update: MemeUpdate = mock(defaultAnswer = RETURNS_DEEP_STUBS) {
            on(it.user.id).thenReturn(111)
            on(it.user.userName).thenReturn("test_user")
            on(it.messageId).thenReturn(222)
            on(it.fileId).thenReturn("random_file_id")
        }

        whenever(telegramUserDao.findById(111)).thenReturn(
            TelegramUser(
                111,
                "test_user",
                null,
                null,
                UserStatus.BANNED
            )
        )

        val replyToBannedUser = SendMessage().apply {
            chatId = "111"
            replyToMessageId = 222
            text = "Мем на привитой модерации"
        }

        whenever(api.execute(replyToBannedUser)).thenReturn(mock())

        val logMessage = SendPhoto().apply {
            chatId = "logsChatId"
            photo = InputFile("random_file_id")
            caption = "мем <a href=\"tg://user?id=111\">test_user</a> отправлен на личную модерацию в НИКУДА"
            parseMode = ParseMode.HTML
            disableNotification = true
        }

        whenever(api.executeAsync(logMessage)).thenReturn(mock())
        memeHandler.handleSync(update)

        verify(telegramUserDao).findById(111)
        verify(api).execute(replyToBannedUser)
        verify(api).executeAsync(logMessage)
        verifyNoMoreInteractions(api, telegramUserDao)
    }
}
