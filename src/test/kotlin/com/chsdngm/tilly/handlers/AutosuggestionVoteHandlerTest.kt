package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.model.AutoSuggestedMemeUpdate
import com.chsdngm.tilly.model.AutosuggestionVoteUpdate
import com.chsdngm.tilly.model.AutosuggestionVoteValue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption
import org.telegram.telegrambots.meta.api.objects.User

class AutosuggestionVoteHandlerTest {
    private val memeHandler = mock(MemeHandler::class.java)
    private val api = mock(TelegramApi::class.java)
    private val telegramProperties = TelegramProperties(
        "montornChatId",
        "targetChatId",
        "targetChannelId",
        "botToken",
        "botUsername",
        "logsChatId",
        777
    )

    private val autosuggestionVoteHandler = AutosuggestionVoteHandler(memeHandler, api, telegramProperties)

    @Test
    fun shouldHandleByMemeHandlerOnPositiveAutosuggestionDecision() {
        val user = mock(User::class.java)
        val memeUpdate = mock(AutoSuggestedMemeUpdate::class.java)
        val update = mock(AutosuggestionVoteUpdate::class.java).apply {
            `when`(whoSuggests).thenReturn(user)
            `when`(fileId).thenReturn("random_file_id")
            `when`(chatId).thenReturn(555)
            `when`(messageId).thenReturn(666)
            `when`(voteValue).thenReturn(AutosuggestionVoteValue.APPROVE_SUGGESTION)
            `when`(toAutoSuggestedMemeUpdate()).thenReturn(memeUpdate)
        }

        val editMessageCaptionMethod = EditMessageCaption().apply {
            chatId = "555"
            messageId = 666
            caption = "мем отправлен в общую предложку, если он не дубликат"
        }

        autosuggestionVoteHandler.handleSync(update)
        verify(update).voteValue
        verify(update).toAutoSuggestedMemeUpdate()
        verify(memeHandler).handle(memeUpdate)
        verify(update).chatId
        verify(update).messageId
        verify(api).execute(editMessageCaptionMethod)
        verifyNoMoreInteractions(memeHandler, api, update)
    }

    @Test
    fun shouldChangeCaptionOnNegativeAutosuggestionDecision() {
        val update = mock(AutosuggestionVoteUpdate::class.java).apply {
            `when`(chatId).thenReturn(555)
            `when`(messageId).thenReturn(666)
            `when`(voteValue).thenReturn(AutosuggestionVoteValue.DECLINE_SUGGESTION)
        }

        val editMessageCaptionMethod = EditMessageCaption().apply {
            chatId = "555"
            messageId = 666
            caption = "мем предан забвению"
        }

        autosuggestionVoteHandler.handleSync(update)
        verify(api).execute(editMessageCaptionMethod)
        verifyNoMoreInteractions(memeHandler, api)
    }
}