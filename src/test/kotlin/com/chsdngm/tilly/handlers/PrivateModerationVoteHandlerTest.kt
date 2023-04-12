package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.model.PrivateVoteUpdate
import com.chsdngm.tilly.model.PrivateVoteValue
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.model.dto.Meme
import com.chsdngm.tilly.model.dto.Vote
import com.chsdngm.tilly.repository.MemeDao
import com.chsdngm.tilly.repository.VoteDao
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.*
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption
import org.telegram.telegrambots.meta.api.objects.InputFile

class PrivateModerationVoteHandlerTest {
    private val memeDao = mock<MemeDao>()
    private val voteDao = mock<VoteDao>()
    private val api = mock<TelegramApi>()
    private val telegramProperties = TelegramProperties(
        "montornChatId",
        "777",
        "targetChannelId",
        "botToken",
        "botUsername",
        "logsChatId",
        777
    )

    private val privateModerationVoteHandler = PrivateModerationVoteHandler(memeDao, voteDao, api, telegramProperties)

    @Test
    fun shouldHandleApproveEvent() {
        val update: PrivateVoteUpdate = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS) {
            on(it.user.id).thenReturn(111)
            on(it.user.userName).thenReturn("user_name")
            on(it.messageId).thenReturn(222)
            on(it.voteValue).thenReturn(PrivateVoteValue.APPROVE)
        }

        val meme = mock<Meme> {
            on(it.id).thenReturn(-1)
            on(it.fileId).thenReturn("fileId")
        }
        val votes = listOf<Vote>(mock())

        whenever(memeDao.findMemeByModerationChatIdAndModerationChatMessageId(111, 222))
            .thenReturn(meme to votes)
        memeDao.stub {
            onBlocking { it.update(meme) }.thenReturn(1)
        }
        whenever(voteDao.insert(Vote(-1, 111, 222, VoteValue.UP))).thenReturn(mock())

        val editMessageCaption: EditMessageCaption = EditMessageCaption().apply {
            chatId = "111"
            messageId = 222
            caption = "мем будет отправлен на канал"
        }

        val sendPhoto: SendPhoto = SendPhoto().apply {
            chatId = "logsChatId"
            photo = InputFile("fileId")
            caption = "<a href=\"tg://user?id=111\">user_name</a> отправил(а) мем на канал"
            parseMode = ParseMode.HTML
            disableNotification = true
        }

        privateModerationVoteHandler.handleSync(update)

        verifyBlocking(api) { executeSuspended(editMessageCaption) }
        verifyBlocking(api) { updateStatsInSenderChat(meme, votes) }
        verifyBlocking(api) { executeSuspended(sendPhoto) }

        verifyBlocking(memeDao) { update(meme) }
        verifyBlocking(memeDao) { memeDao.findMemeByModerationChatIdAndModerationChatMessageId(111, 222) }

        verify(voteDao).insert(Vote(-1, 111, 222, VoteValue.UP))
        verifyNoMoreInteractions(memeDao, voteDao, api)
    }

    @Test
    fun shouldHandleDeclineEvent() {
        val update: PrivateVoteUpdate = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS) {
            on(it.user.id).thenReturn(111)
            on(it.user.userName).thenReturn("user_name")
            on(it.messageId).thenReturn(222)
            on(it.voteValue).thenReturn(PrivateVoteValue.DECLINE)
        }

        val meme = mock<Meme> {
            on(it.id).thenReturn(-1)
            on(it.fileId).thenReturn("fileId")
        }
        val votes = listOf<Vote>(mock())

        whenever(memeDao.findMemeByModerationChatIdAndModerationChatMessageId(111, 222))
            .thenReturn(meme to votes)
        memeDao.stub {
            onBlocking { it.update(meme) }.thenReturn(1)
        }
        whenever(voteDao.insert(Vote(-1, 111, 222, VoteValue.UP))).thenReturn(mock())

        val editMessageCaption = EditMessageCaption().apply {
            chatId = "111"
            messageId = 222
            caption = "мем предан забвению"
        }

        val sendPhoto = SendPhoto().apply {
            chatId = "logsChatId"
            photo = InputFile("fileId")
            caption = "<a href=\"tg://user?id=111\">user_name</a> предал(а) мем забвению"
            parseMode = ParseMode.HTML
            disableNotification = true
        }

        privateModerationVoteHandler.handleSync(update)

        verifyBlocking(api) { executeSuspended(editMessageCaption) }
        verifyBlocking(api) { updateStatsInSenderChat(meme, votes) }
        verifyBlocking(api) { executeSuspended(sendPhoto) }

        verify(voteDao).insert(Vote(-1, 111, 222, VoteValue.DOWN))

        verifyBlocking(memeDao) { update(meme) }
        verifyBlocking(memeDao) { memeDao.findMemeByModerationChatIdAndModerationChatMessageId(111, 222) }

        verifyNoMoreInteractions(memeDao, voteDao, api)
    }
}
