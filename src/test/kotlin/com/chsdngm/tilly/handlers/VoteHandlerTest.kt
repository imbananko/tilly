package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.metrics.MetricsUtils
import com.chsdngm.tilly.model.VoteUpdate
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.model.dto.Meme
import com.chsdngm.tilly.repository.MemeDao
import com.chsdngm.tilly.repository.VoteDao
import com.chsdngm.tilly.schedulers.ChannelMarkupUpdater
import javassist.NotFoundException
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery

class VoteHandlerTest {
    private val memeDao = mock<MemeDao>()
    private val voteDao = mock<VoteDao>()
    private val metricsUtils = mock<MetricsUtils>()
    private val channelMarkupUpdater = mock<ChannelMarkupUpdater>()
    private val api = mock<TelegramApi>()
    private val telegramProperties = TelegramProperties(
        "montornChatId",
        "targetChatId",
        "targetChannelId",
        "botToken",
        "botUsername",
        "logsChatId",
        777
    )

    private val voteHandler = VoteHandler(memeDao, voteDao, metricsUtils, channelMarkupUpdater, api, telegramProperties)

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

        whenever(api.execute(answerCallbackQuery)).thenReturn(true)

        voteHandler.handleSync(update)

        verify(api).execute(answerCallbackQuery)
        verifyNoMoreInteractions(memeDao, voteDao, metricsUtils, channelMarkupUpdater, api)
    }

    @Test
    fun shouldThrowExceptionWhenMemeNotFound() {
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
            on(it.sourceChatId).thenReturn("targetChannelId")
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
        whenever(api.execute(answerCallbackQuery)).thenReturn(true)

        voteHandler.handleSync(update)

        verify(memeDao).findMemeByChannelMessageId(555)
        verify(api).execute(answerCallbackQuery)
        verifyNoMoreInteractions(memeDao, voteDao, metricsUtils, channelMarkupUpdater, api)
    }

    @Test
    @Disabled("TODO fix after remove static field as properties")
    fun shouldProcessVoteFromChannel() {
        val update = mock(VoteUpdate::class.java).apply {
            `when`(isOld).thenReturn(false)
            `when`(sourceChatId).thenReturn("random_source_chat_id")
            `when`(voterId).thenReturn(444)
            `when`(messageId).thenReturn(555)
            `when`(callbackQueryId).thenReturn("random_callback")
            `when`(voteValue).thenReturn(VoteValue.UP)
        }

        val meme = mock(Meme::class.java).apply {
            `when`(channelMessageId).thenReturn(1010)
        }

        `when`(memeDao.findMemeByChannelMessageId(555)).thenReturn(meme to listOf())

        voteHandler.handleSync(update)

        verify(memeDao.findMemeByChannelMessageId(555))
        verify(channelMarkupUpdater.submitVote(any()))

    }

    @Test
    fun shouldProcessVoteFromGroup() {
        //TODO
    }

}