package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.metrics.MetricsUtils
import com.chsdngm.tilly.model.AutoSuggestedMemeUpdate
import com.chsdngm.tilly.model.MemeUpdate
import com.chsdngm.tilly.model.UserStatus
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.model.dto.Meme
import com.chsdngm.tilly.model.dto.TelegramUser
import com.chsdngm.tilly.model.dto.Vote
import com.chsdngm.tilly.repository.DistributedModerationEventDao
import com.chsdngm.tilly.repository.MemeDao
import com.chsdngm.tilly.repository.TelegramUserDao
import com.chsdngm.tilly.repository.VoteDao
import com.chsdngm.tilly.similarity.ImageService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.Mockito.any
import org.mockito.Mockito.verify
import org.mockito.kotlin.*
import org.mockito.kotlin.verifyNoMoreInteractions
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.MemberStatus
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import java.io.File
import java.time.Instant


//TODO: work in progress...
class MemeHandlerTest {
    private val telegramUserDao = mock<TelegramUserDao>()
    private val memeDao = mock<MemeDao>()
    private val voteDao = mock<VoteDao>()
    private val distributedModerationEventDao = mock<DistributedModerationEventDao>()
    private val metricsUtils = mock<MetricsUtils>()
    private val api = mock<TelegramApi>()
    private val imageService = mock<ImageService>()

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
        memeDao,
        voteDao,
        distributedModerationEventDao,
        metricsUtils,
        api,
        imageService,
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
        whenever(imageService.tryFindDuplicate(file)).thenReturn(null)

        val message = mock<Message> { on(it.messageId).thenReturn(0) }
        whenever(api.execute(any<SendPhoto>())).thenReturn(message)
        whenever(api.executeAsync(any<SendPhoto>())).thenReturn(mock())
        telegramUserDao.stub {
            whenever(it.findById(111)).thenReturn(
                TelegramUser(111, "old_name", null, null)
            )
            onBlocking { findUsersWithRecentlyPrivateModerationAssignment() }.thenReturn((0..5).map { mock() })
        }


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

    @Test
    fun shouldInsertApproveVoteWhenAutoSuggestion() {
        val approverId = 999L

        val autoSuggestedMemeUpdate: AutoSuggestedMemeUpdate = mock(defaultAnswer = RETURNS_DEEP_STUBS) {
            on(it.user.id).thenReturn(telegramProperties.botId)
            on(it.user.userName).thenReturn("tilly_bot_user")
            on(it.messageId).thenReturn(222)
            on(it.fileId).thenReturn("random_file_id")
            on(it.approver.id).thenReturn(approverId)
        }

        val file = File.createTempFile("photo-", "${Thread.currentThread().id}").apply {
            deleteOnExit()
        }
        whenever(api.download("random_file_id")).thenReturn(file)
        whenever(imageService.tryFindDuplicate(file)).thenReturn(null)

        val replyKeyboardMarkup = InlineKeyboardMarkup().apply {
            keyboard = listOf(
                listOf(
                    InlineKeyboardButton().also {
                        it.text = "${VoteValue.UP.emoji} 1"
                        it.callbackData = VoteValue.UP.name
                    },
                    InlineKeyboardButton().also {
                        it.text = VoteValue.DOWN.emoji
                        it.callbackData = VoteValue.DOWN.name
                    })
            )
        }
        val photo = SendPhoto().apply {
            chatId = telegramProperties.targetChatId
            photo = InputFile("random_file_id")
            caption = "Sender: montorn"
            parseMode = ParseMode.HTML
            replyMarkup = replyKeyboardMarkup
        }

        val memeMessageId = 100500
        val message = mock<Message> { on(it.messageId).thenReturn(memeMessageId) }
        val botUserMock = mock<User> {
            on(it.isBot).thenReturn(true)
            on(it.id).thenReturn(telegramProperties.botId)
        }
        val chatMember = mock<ChatMember> {
            on(it.status).thenReturn(MemberStatus.ADMINISTRATOR)
            on(it.user).thenReturn(botUserMock)
        }

        whenever(api.execute(any<GetChatMember>())).thenReturn(chatMember)
        whenever(api.execute(photo)).thenReturn(message)

        val memeId = 111111
        val createdMeme = mock<Meme> {
            on(it.id).thenReturn(memeId)
        }

        whenever(memeDao.insert(isA<Meme>())).thenReturn(createdMeme)

        memeHandler.handle(autoSuggestedMemeUpdate)

        val vote = Vote(memeId, approverId, telegramProperties.targetChatId.toLong(), VoteValue.UP, Instant.EPOCH)

        verifyBlocking(voteDao) { insert(vote) }
        verifyBlocking(memeDao) { (insert(isA<Meme>())) }
        verify(imageService).tryFindDuplicate(isA<File>())
        verifyBlocking(imageService) { handleImage(isA<MemeUpdate>(), isA<File>()) }
        verifyNoMoreInteractions(voteDao, memeDao, imageService)
    }
}
