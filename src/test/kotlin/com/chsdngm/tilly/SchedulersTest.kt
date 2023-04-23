package com.chsdngm.tilly

import com.chsdngm.tilly.config.MetadataProperties
import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.model.MemeStatus
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.model.dto.Meme
import com.chsdngm.tilly.model.dto.Vote
import com.chsdngm.tilly.repository.MemeDao
import com.chsdngm.tilly.repository.MemeLogDao
import com.chsdngm.tilly.repository.TelegramUserDao
import com.chsdngm.tilly.schedulers.Schedulers
import com.chsdngm.tilly.similarity.ElasticsearchService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.groupadministration.SetChatTitle
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

class SchedulersTest {
    private val telegramUserDao = mock<TelegramUserDao>()
    private val memeDao = mock<MemeDao>()
    private val api = mock<TelegramApi>()
    private val memeLogDao = mock<MemeLogDao>()
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

    private val metadataProperties = MetadataProperties(
        "1",
        "hui"
    )

    private val schedulers =
        Schedulers(memeDao, telegramUserDao, memeLogDao, elasticsearchService, api, telegramProperties, metadataProperties)

    @Test
    fun shouldNotPublishAnythingWhenPublishingMemesDisabled() {
        telegramProperties.publishingEnabled = false

        schedulers.publishMeme()

        verifyNoMoreInteractions(telegramUserDao, memeDao, api, memeLogDao, elasticsearchService)
    }

    @Test
    fun shouldNotPublishAnythingWhenThereAreNoMemes() {
        telegramProperties.publishingEnabled = true

        memeDao.stub {
            onBlocking { it.findAllByStatusOrderByCreated(MemeStatus.SCHEDULED) }.thenReturn(mapOf())
        }

        schedulers.publishMeme()

        verifyBlocking(memeDao) {
            findAllByStatusOrderByCreated(MemeStatus.SCHEDULED)
        }
        verifyNoMoreInteractions(telegramUserDao, memeDao, api, memeLogDao, elasticsearchService)
    }

    @Test
    fun shouldSendMemeWhenThereAreMemes() {
        telegramProperties.publishingEnabled = true

        val meme = mock<Meme> {
            on(it.fileId).thenReturn("random_file_id")
            on(it.caption).thenReturn("random_caption")
            whenever(it.channelMessageId).thenReturn(666)
            whenever(it.status).thenReturn(MemeStatus.PUBLISHED)
        }

        val votes = (0..4).map { mock<Vote> { on(it.value).thenReturn(VoteValue.UP) } }
        val memes = mapOf(meme to votes)

        memeDao.stub {
            onBlocking { it.findAllByStatusOrderByCreated(MemeStatus.SCHEDULED) }.thenReturn(memes)
            onBlocking { it.update(meme) }.thenReturn(1)
        }

        val sendPhoto = SendPhoto().apply {
            chatId = "targetChannelId"
            photo = InputFile("random_file_id")
            replyMarkup = InlineKeyboardMarkup().apply {
                keyboard = listOf(
                    listOf(
                        InlineKeyboardButton().also {
                            it.text = "${VoteValue.UP.emoji} 5"
                            it.callbackData = "UP"
                        },
                        InlineKeyboardButton().also {
                            it.text = VoteValue.DOWN.emoji
                            it.callbackData = "DOWN"
                        }
                    )
                )
                parseMode = ParseMode.HTML
                caption = "random_caption"
            }
        }

        val message = mock<Message> {
            on(it.messageId).thenReturn(666)
        }

        api.stub {
            onBlocking { it.executeSuspended(sendPhoto) }.thenReturn(message)
        }

        schedulers.publishMeme()

        verifyBlocking(api) { executeSuspended(sendPhoto) }
        verifyBlocking(api) { updateStatsInSenderChat(meme, votes) }

        verifyBlocking(memeDao) { findAllByStatusOrderByCreated(MemeStatus.SCHEDULED) }
        verifyBlocking(memeDao) { update(meme) }

        verifyBlocking(api) {
            executeSuspended(SetChatTitle().apply {
                chatId = "logsChatId"
                title = "tilly.log | queued: 0 [hui]"
            })
        }

        verifyNoMoreInteractions(telegramUserDao, memeDao, api, memeLogDao, elasticsearchService)
    }

    @Test
    fun shouldNotScheduleAnythingWhenThereNoMemesForScheduling() {
        whenever(memeDao.scheduleMemes()).thenReturn(listOf())
        schedulers.scheduleMemesIfAny()

        verify(memeDao).scheduleMemes()
        verifyNoMoreInteractions(telegramUserDao, memeDao, api, memeLogDao, elasticsearchService)
    }

    @Test
    fun shouldScheduleMemesWhenThereMemesForScheduling() {
        val memes = (0..4).map { number ->
            mock<Meme> {
                on(it.moderationChatId).thenReturn(number.toLong())
                on(it.moderationChatMessageId).thenReturn(number)
            }
        }

        memeDao.stub {
            whenever(it.scheduleMemes()).thenReturn(memes)
            onBlocking { findAllByStatusOrderByCreated(MemeStatus.SCHEDULED) }.thenReturn(mapOf())
        }

        val setChatTitle = SetChatTitle().apply {
            chatId = "logsChatId"
            title = "tilly.log | queued: 0 [hui]"
        }

        api.stub {
            onBlocking { executeSuspended(setChatTitle) }.thenReturn(true)
        }

        schedulers.scheduleMemesIfAny()

        verify(memeDao).scheduleMemes()
        verifyBlocking(memeDao) { findAllByStatusOrderByCreated(MemeStatus.SCHEDULED) }
        verifyBlocking(api) { executeSuspended(setChatTitle) }

        repeat((0..4).count()) {
            val editMessageReplyMarkup = EditMessageReplyMarkup().apply {
                chatId = "$it"
                messageId = it
            }

            verifyBlocking(api) { executeSuspended(editMessageReplyMarkup) }
        }
        verifyNoMoreInteractions(telegramUserDao, memeDao, api, memeLogDao, elasticsearchService)
    }
}
