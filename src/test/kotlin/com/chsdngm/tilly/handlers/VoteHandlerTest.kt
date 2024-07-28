package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.metrics.MetricsUtils
import com.chsdngm.tilly.model.VoteUpdate
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.model.dto.Meme
import com.chsdngm.tilly.model.dto.Vote
import com.chsdngm.tilly.repository.MemeDao
import com.chsdngm.tilly.repository.VoteDao
import com.chsdngm.tilly.schedulers.ChannelMarkupUpdater
import com.chsdngm.tilly.createMarkup
import com.chsdngm.tilly.repository.InstagramReelDao
import javassist.NotFoundException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import java.io.Serializable
import java.time.Instant

class VoteHandlerTest {
    private val memeDao = mock<MemeDao>()
    private val voteDao = mock<VoteDao>()
    private val instagramReelDao = mock<InstagramReelDao>()
    private val metricsUtils = mock<MetricsUtils>()
    private val channelMarkupUpdater = mock<ChannelMarkupUpdater>()
    private val api = mock<TelegramApi>()
    private val telegramProperties = TelegramProperties(
        "montornChatId",
        "666",
        "2105",
        "botToken",
        "botUsername",
        "logsChatId",
        777
    )

    private val voteHandler = VoteHandler(memeDao, instagramReelDao, voteDao, metricsUtils, channelMarkupUpdater, api, telegramProperties)

    @Test
    fun shouldSendNotificationWhenVotingTooOldMeme() {
        val update = mock<VoteUpdate> {
            on(it.isOld).thenReturn(true)
            on(it.callbackQueryId).thenReturn("random_callback")
        }

        val answerCallbackQuery = AnswerCallbackQuery().apply {
            callbackQueryId = "random_callback"
            text = "Мем слишком стар"
        }

        api.stub {
            onBlocking { executeSuspended(answerCallbackQuery) }.thenReturn(true)
        }

        voteHandler.handleSync(update)

        verifyBlocking(api) { api.executeSuspended(answerCallbackQuery) }
        verifyNoMoreInteractions(memeDao, voteDao, metricsUtils, channelMarkupUpdater, api)
    }

    @Test
    fun shouldThrowWhenMemeNotFound() {
        val update = mock<VoteUpdate> {
            on(it.isOld).thenReturn(false)
            on(it.sourceChatId).thenReturn("bad_source_chat_id")
        }

        assertThrows<NotFoundException> { voteHandler.handleSync(update) }
        verifyNoMoreInteractions(memeDao, voteDao, metricsUtils, channelMarkupUpdater, api)
    }

    @Test
    fun shouldSendNotificationWhenSelfVoting() {
        val update = mock<VoteUpdate> {
            on(it.isOld).thenReturn(false)
            on(it.sourceChatId).thenReturn("2105")
            on(it.voterId).thenReturn(444)
            on(it.messageId).thenReturn(555)
            on(it.callbackQueryId).thenReturn("random_callback")
        }

        val meme = mock<Meme> {
            on(it.senderId).thenReturn(444)
        }

        val answerCallbackQuery = AnswerCallbackQuery().apply {
            callbackQueryId = "random_callback"
            text = "Голосуй за других, а не за себя"
        }

        whenever(memeDao.findMemeByChannelMessageId(555)).thenReturn(meme to listOf())
        api.stub {
            onBlocking { executeSuspended(answerCallbackQuery) }.thenReturn(true)
        }

        voteHandler.handleSync(update)

        verify(memeDao).findMemeByChannelMessageId(555)
        verifyBlocking(api) { api.executeSuspended(answerCallbackQuery) }
        verifyNoMoreInteractions(memeDao, voteDao, metricsUtils, channelMarkupUpdater, api)
    }

    @Test
    fun shouldProcessVoteFromChannel() {
        val update = mock<VoteUpdate> {
            on(it.isOld).thenReturn(false)
            on(it.sourceChatId).thenReturn("2105")
            on(it.voterId).thenReturn(444)
            on(it.messageId).thenReturn(555)
            on(it.callbackQueryId).thenReturn("random_callback")
            on(it.voteValue).thenReturn(VoteValue.UP)
            on(it.createdAt).thenReturn(1234567)
        }

        val meme = mock<Meme> {
            whenever(it.channelMessageId).thenReturn(1010)
            whenever(it.id).thenReturn(1)
        }

        whenever(memeDao.findMemeByChannelMessageId(555)).thenReturn(meme to listOf())

        val answerCallbackQuery = AnswerCallbackQuery().apply {
            callbackQueryId = "random_callback"
            text = "Вы обогатили этот мем \uD83D\uDC8E"
        }

        api.stub {
            onBlocking { executeSuspended(answerCallbackQuery) }.thenReturn(true)
        }

        voteHandler.handleSync(update)

        val vote = Vote(
            1,
            444,
            2105,
            VoteValue.UP,
            Instant.ofEpochMilli(1234567)
        )

        verify(memeDao).findMemeByChannelMessageId(555)
        verifyBlocking(channelMarkupUpdater) { submitVote(1010 to listOf(vote)) }
        verifyBlocking(voteDao) { insert(vote) }
        verifyBlocking(api) { api.executeSuspended(answerCallbackQuery) }
        verifyBlocking(api) { api.updateStatsInSenderChat(meme, listOf(vote)) }
        verifyNoMoreInteractions(memeDao, voteDao, metricsUtils, channelMarkupUpdater, api)
    }

    @Test
    fun shouldProcessVoteFromGroup() {
        val update = mock<VoteUpdate> {
            on(it.isOld).thenReturn(false)
            on(it.sourceChatId).thenReturn("666")
            on(it.voterId).thenReturn(2103)
            on(it.messageId).thenReturn(555)
            on(it.callbackQueryId).thenReturn("random_callback")
            on(it.voteValue).thenReturn(VoteValue.DOWN)
            on(it.createdAt).thenReturn(1234567)
        }

        val meme = mock<Meme> {
            whenever(it.channelMessageId).thenReturn(null)
            whenever(it.id).thenReturn(1)
        }

        whenever(memeDao.findMemeByModerationChatIdAndModerationChatMessageId(666, 555))
            .thenReturn(meme to listOf())

        val answerCallbackQuery = AnswerCallbackQuery().apply {
            callbackQueryId = "random_callback"
            text = "Вы засрали этот мем \uD83D\uDCA9"
        }

        val vote = Vote(
            1,
            2103,
            666,
            VoteValue.DOWN,
            Instant.ofEpochMilli(1234567)
        )

        val editMessageReplyMarkup = EditMessageReplyMarkup().apply {
            chatId = telegramProperties.targetChatId
            messageId = meme.moderationChatMessageId
            replyMarkup = createMarkup(listOf(vote))
        }

        api.stub {
            onBlocking { executeSuspended(answerCallbackQuery) }.thenReturn(true)
            onBlocking { executeSuspended(editMessageReplyMarkup) }.thenReturn(true)
        }

        voteHandler.handleSync(update)

        verify(memeDao).findMemeByModerationChatIdAndModerationChatMessageId(666, 555)
        verifyBlocking(voteDao) { insert(vote) }
        verifyBlocking(api) { api.executeSuspended(answerCallbackQuery) }
        verifyBlocking(api) { api.executeSuspended(editMessageReplyMarkup) }
        verifyBlocking(api) { api.updateStatsInSenderChat(meme, listOf(vote)) }
        verifyNoMoreInteractions(memeDao, voteDao, metricsUtils, channelMarkupUpdater, api)
    }

    @Test
    fun shouldRemoveVoteWhenTheSameVoterAndValue() {
        val update = mock<VoteUpdate> {
            on(it.isOld).thenReturn(false)
            on(it.sourceChatId).thenReturn("666")
            on(it.voterId).thenReturn(2103)
            on(it.messageId).thenReturn(555)
            on(it.callbackQueryId).thenReturn("random_callback")
            on(it.voteValue).thenReturn(VoteValue.DOWN)
            on(it.createdAt).thenReturn(1234567)
        }

        val meme = mock<Meme> {
            whenever(it.channelMessageId).thenReturn(null)
            whenever(it.moderationChatMessageId).thenReturn(321)
            whenever(it.id).thenReturn(1)
        }

        val vote = Vote(
            1,
            2103,
            666,
            VoteValue.DOWN,
            Instant.ofEpochMilli(1234567)
        )

        whenever(memeDao.findMemeByModerationChatIdAndModerationChatMessageId(666, 555))
            .thenReturn(meme to listOf(vote))

        val answerCallbackQuery = AnswerCallbackQuery().apply {
            callbackQueryId = "random_callback"
            text = "Вы удалили свой голос с этого мема"
        }

        val editMessageReplyMarkup = EditMessageReplyMarkup().apply {
            chatId = telegramProperties.targetChatId
            messageId = 321
            replyMarkup = createMarkup()
        }

        api.stub {
            onBlocking { executeSuspended(answerCallbackQuery) }.thenReturn(true)
            onBlocking { executeSuspended(editMessageReplyMarkup) }.thenReturn(mock<Serializable>())
        }

        voteHandler.handleSync(update)

        verify(memeDao).findMemeByModerationChatIdAndModerationChatMessageId(666, 555)
        verifyBlocking(voteDao) { delete(vote) }
        verifyBlocking(api) { api.executeSuspended(answerCallbackQuery) }
        verifyBlocking(api) { api.executeSuspended(editMessageReplyMarkup) }
        verifyBlocking(api) { api.updateStatsInSenderChat(meme, listOf()) }
        verifyNoMoreInteractions(memeDao, voteDao, metricsUtils, channelMarkupUpdater, api)
    }

    @Test
    fun shouldUpdateVoteWhenTheSameVoterAndDifferentValue() {
        val update = mock<VoteUpdate> {
            on(it.isOld).thenReturn(false)
            on(it.sourceChatId).thenReturn("666")
            on(it.voterId).thenReturn(2103)
            on(it.messageId).thenReturn(555)
            on(it.callbackQueryId).thenReturn("random_callback")
            on(it.voteValue).thenReturn(VoteValue.DOWN)
            on(it.createdAt).thenReturn(1234567)
        }

        val meme = mock<Meme> {
            whenever(it.channelMessageId).thenReturn(null)
            whenever(it.moderationChatMessageId).thenReturn(321)
            whenever(it.id).thenReturn(1)
        }

        val old = Vote(
            1,
            2103,
            666,
            VoteValue.UP,
            Instant.ofEpochMilli(1234567)
        )

        whenever(memeDao.findMemeByModerationChatIdAndModerationChatMessageId(666, 555))
            .thenReturn(meme to listOf(old))

        val new = Vote(
            1,
            2103,
            666,
            VoteValue.DOWN,
            Instant.ofEpochMilli(1234567)
        )

        val answerCallbackQuery = AnswerCallbackQuery().apply {
            callbackQueryId = "random_callback"
            text = "Вы засрали этот мем \uD83D\uDCA9"
        }

        val editMessageReplyMarkup = EditMessageReplyMarkup().apply {
            chatId = telegramProperties.targetChatId
            messageId = 321
            replyMarkup = createMarkup(listOf(new))
        }

        api.stub {
            onBlocking { executeSuspended(answerCallbackQuery) }.thenReturn(true)
            onBlocking { executeSuspended(editMessageReplyMarkup) }.thenReturn(mock<Serializable>())
        }

        voteHandler.handleSync(update)

        verify(memeDao).findMemeByModerationChatIdAndModerationChatMessageId(666, 555)
        verifyBlocking(voteDao) { update(new) }
        verifyBlocking(api) { api.executeSuspended(answerCallbackQuery) }
        verifyBlocking(api) { api.executeSuspended(editMessageReplyMarkup) }
        verifyBlocking(api) { api.updateStatsInSenderChat(meme, listOf(new)) }
        verifyNoMoreInteractions(memeDao, voteDao, metricsUtils, channelMarkupUpdater, api)
    }

}