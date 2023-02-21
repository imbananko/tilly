package com.chsdngm.tilly.handlers

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
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import java.util.concurrent.CompletableFuture

class VoteHandlerTest {
    private val memeDao = mock(MemeDao::class.java)
    private val voteDao = mock(VoteDao::class.java)
    private val metricsUtils = mock(MetricsUtils::class.java)
    private val channelMarkupUpdater = mock(ChannelMarkupUpdater::class.java)
    private val api = mock(DefaultAbsSender::class.java)

    private val voteHandler = VoteHandler(memeDao, voteDao, metricsUtils, channelMarkupUpdater, api)

    @Test
    fun shouldSendNotificationWhenVotingTooOldMeme() {
        val update = mock(VoteUpdate::class.java).apply {
            `when`(isOld).thenReturn(true)
            `when`(callbackQueryId).thenReturn("random_callback")
        }

        val expectedMethod = AnswerCallbackQuery().apply {
            cacheTime = 0
            callbackQueryId = "random_callback"
            text = "Мем слишком стар"
        }

        `when`(api.executeAsync(expectedMethod)).thenReturn(CompletableFuture.completedFuture(true))

        voteHandler.handleSync(update)

        verify(api).executeAsync(expectedMethod)
        verifyNoMoreInteractions(memeDao, voteDao, metricsUtils, channelMarkupUpdater, api)
    }

    @Test
    fun shouldThrowExceptionWhenMemeNotFound() {
        val update = mock(VoteUpdate::class.java).apply {
            `when`(isOld).thenReturn(false)
            `when`(sourceChatId).thenReturn("bad_source_chat_id")
        }

        assertThrows<NotFoundException> { voteHandler.handleSync(update) }
        verifyNoMoreInteractions(memeDao, voteDao, metricsUtils, channelMarkupUpdater, api)
    }

    @Test
    fun shouldSendNotificationWhenSelfVoting() {
        val update = mock(VoteUpdate::class.java).apply {
            `when`(isOld).thenReturn(false)
            //TODO fix after remove static field as properties
            `when`(sourceChatId).thenReturn("")
            `when`(voterId).thenReturn(444)
            `when`(messageId).thenReturn(555)
            `when`(callbackQueryId).thenReturn("random_callback")
        }

        val meme = mock(Meme::class.java).apply {
            `when`(senderId).thenReturn(444)
        }

        val expectedMethod = AnswerCallbackQuery().apply {
            cacheTime = 0
            callbackQueryId = "random_callback"
            text = "Голосуй за других, а не за себя"
        }

        `when`(memeDao.findMemeByChannelMessageId(555)).thenReturn(meme to listOf())
        `when`(api.executeAsync(expectedMethod)).thenReturn(CompletableFuture.completedFuture(true))

        voteHandler.handleSync(update)

        verify(memeDao).findMemeByChannelMessageId(555)
        verify(api).executeAsync(expectedMethod)
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