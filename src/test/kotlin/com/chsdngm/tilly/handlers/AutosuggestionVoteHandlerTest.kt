package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.model.AutoSuggestedMemeUpdate
import com.chsdngm.tilly.model.AutosuggestionVoteUpdate
import com.chsdngm.tilly.model.AutosuggestionVoteValue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.mock
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
        val memeUpdate = mock(AutoSuggestedMemeUpdate::class.java)
        val update = mock<AutosuggestionVoteUpdate> {
            on(it.whoSuggests).thenReturn(mock<User>())
            on(it.fileId).thenReturn("random_file_id")
            on(it.chatId).thenReturn(555)
            on(it.messageId).thenReturn(666)
            on(it.voteValue).thenReturn(AutosuggestionVoteValue.APPROVE_SUGGESTION)
            on(it.toAutoSuggestedMemeUpdate()).thenReturn(memeUpdate)
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
        val update = mock<AutosuggestionVoteUpdate> {
            on(it.chatId).thenReturn(555)
            on(it.messageId).thenReturn(666)
            on(it.voteValue).thenReturn(AutosuggestionVoteValue.DECLINE_SUGGESTION)
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