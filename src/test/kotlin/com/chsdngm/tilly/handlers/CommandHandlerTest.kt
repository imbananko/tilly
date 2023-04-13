package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.model.CommandUpdate
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.model.dto.Meme
import com.chsdngm.tilly.model.dto.Vote
import com.chsdngm.tilly.repository.MemeDao
import com.chsdngm.tilly.repository.TelegramUserDao
import com.chsdngm.tilly.repository.VoteDao
import com.chsdngm.tilly.utility.minusDays
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import java.time.Instant

class CommandHandlerTest {
    private val telegramUserDao = mock<TelegramUserDao>()
    private val memeDao = mock<MemeDao>()
    private val voteDao = mock<VoteDao>()
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

    private val commandHandler = CommandHandler(telegramUserDao, memeDao, voteDao, mock(), api, telegramProperties)

    @Test
    fun shouldSendInfoMessageWhenHelpOrStartCommandReceived() {
        val update = mock<CommandUpdate> {
            on(it.value).thenReturn(CommandUpdate.Command.HELP)
            on(it.messageId).thenReturn(666)
            on(it.senderId).thenReturn("senderId")
        }

        val sendMessageMethod = SendMessage().apply {
            chatId = update.senderId
            parseMode = ParseMode.HTML
            text = """
                Привет, я botUsername. 

                Чат со мной - это место для твоих лучших мемов, которыми охота поделиться.
                Сейчас же отправляй мне самый крутой мем, и, если он пройдёт модерацию, то попадёт на канал <a href="https://t.me/chsdngm/">че с деньгами</a>. 
                Мем, набравший за неделю больше всех кристаллов, станет <b>мемом недели</b>, а его обладатель получит бесконечный респект и поздравление на канале.

                Термины и определения:

                Мем (англ. meme) — единица культурной информации, имеющей развлекательный характер (в нашем случае - картинка). 
                Модерация (от лат. moderor — умеряю, сдерживаю) — все мемы проходят предварительную оценку экспертов, а на канал попадут только лучшие. 

                За динамикой оценки также можно следить тут.
            """.trimIndent()
        }

        commandHandler.handleSync(update)

        whenever(update.value).thenReturn(CommandUpdate.Command.START)

        commandHandler.handleSync(update)

        verify(api, times(2)).execute(sendMessageMethod)
        verifyNoMoreInteractions(telegramUserDao, memeDao, voteDao, api)
    }

    @Test
    fun shouldEnablePublicationWhenConfigCommandReceived() {
        val update = mock<CommandUpdate> {
            on(it.value).thenReturn(CommandUpdate.Command.CONFIG)
            on(it.messageId).thenReturn(666)
            on(it.chatId).thenReturn("logsChatId")
            on(it.text).thenReturn("enable publishing")
        }

        commandHandler.handleSync(update)

        val sendMessageMethod = SendMessage().apply {
            chatId = "logsChatId"
            parseMode = ParseMode.HTML
            replyToMessageId = update.messageId
            text = "Публикация мемов включена"
        }

        verify(api).execute(sendMessageMethod)
        verifyNoMoreInteractions(telegramUserDao, memeDao, voteDao, api)
    }

    @Test
    fun shouldDisablePublicationWhenConfigCommandReceived() {
        val update = mock<CommandUpdate> {
            on(it.value).thenReturn(CommandUpdate.Command.CONFIG)
            on(it.messageId).thenReturn(666)
            on(it.chatId).thenReturn("logsChatId")
            on(it.text).thenReturn("disable publishing")
        }

        commandHandler.handleSync(update)

        val sendMessageMethod = SendMessage().apply {
            chatId = "logsChatId"
            parseMode = ParseMode.HTML
            replyToMessageId = update.messageId
            text = "Публикация мемов выключена"
        }

        verify(api).execute(sendMessageMethod)
        verifyNoMoreInteractions(telegramUserDao, memeDao, voteDao, api)
    }

    @Test
    fun shouldDoNothingOnUnknownCommand() {
        val update = mock<CommandUpdate>()

        whenever(update.value).thenReturn(null)
        commandHandler.handleSync(update)
        verifyNoMoreInteractions(telegramUserDao, memeDao, voteDao, api)
    }

    @Test
    fun shouldSendZeroStatsMessageOnStatsCommandWhenNoData() {
        val update = mock<CommandUpdate> {
            on(it.value).thenReturn(CommandUpdate.Command.STATS)
            on(it.messageId).thenReturn(666)
            on(it.senderId).thenReturn("777")
        }

        memeDao.stub {
            onBlocking { findAllBySenderId(777) }.thenReturn(mapOf())
        }
        voteDao.stub {
            onBlocking { findAllByVoterId(777) }.thenReturn(listOf())
        }

        val sendMessageMethod = SendMessage().apply {
            parseMode = ParseMode.HTML
            chatId = update.senderId
            text = "Статистика недоступна. Отправляй и оценивай мемы!"
        }

        commandHandler.handleSync(update)
        verifyBlocking(api) { executeSuspended(sendMessageMethod) }
        verifyBlocking(memeDao) { findAllBySenderId(777) }
        verifyBlocking(voteDao) { findAllByVoterId(777) }
        verifyNoMoreInteractions(telegramUserDao, memeDao, voteDao, api)
    }

    @Test
    fun shouldSendWeekAndGlobalStatsMessageOnStatsCommand() {
        val update = mock<CommandUpdate> {
            on(it.value).thenReturn(CommandUpdate.Command.STATS)
            on(it.messageId).thenReturn(666)
            on(it.senderId).thenReturn("777")
        }

        val freshMeme = mock<Meme> {
            on(it.created).thenReturn(Instant.now())
        }

        val oldMeme =  mock<Meme> {
            on(it.created).thenReturn(Instant.now().minusDays(10)) // more than a week ago
        }

        val freshLike = mock<Vote> {
            on(it.value).thenReturn(VoteValue.UP)
            on(it.created).thenReturn(Instant.now()) // more than a week ago
        }

        val freshDislike = mock<Vote> {
            on(it.value).thenReturn(VoteValue.DOWN)
            on(it.created).thenReturn(Instant.now()) // more than a week ago
        }

        val oldLike = mock<Vote> {
            on(it.value).thenReturn(VoteValue.UP)
            on(it.created).thenReturn(Instant.now().minusDays(10)) // more than a week ago
        }

        val oldDislike = mock<Vote> {
            on(it.value).thenReturn(VoteValue.DOWN)
            on(it.created).thenReturn(Instant.now().minusDays(10)) // more than a week ago
        }

        val memesWithVotes = mapOf(
            freshMeme to listOf(freshLike, freshLike, freshLike),
            freshMeme to listOf(freshLike, freshLike, freshDislike),
            freshMeme to listOf(freshLike, freshDislike, freshDislike),

            oldMeme to listOf(freshLike, freshLike, freshLike),
            oldMeme to listOf(freshLike, freshLike, freshDislike),
            oldMeme to listOf(freshLike, freshDislike, freshDislike)
        )

        memeDao.stub {
            onBlocking { findAllBySenderId(777) }.thenReturn(memesWithVotes)
        }

        val votes = listOf(
            freshLike,
            freshLike,
            freshDislike,
            freshDislike,
            freshDislike,

            oldLike,
            oldDislike,
            oldDislike

        )

        voteDao.stub {
            onBlocking { findAllByVoterId(777) }.thenReturn(votes)
        }

        telegramUserDao.stub {
            onBlocking { findUserRank("777") }.thenReturn(9)
            onBlocking { findUserRank("777", 7) }.thenReturn(3)
        }

        val sendMessageMethod = SendMessage().apply {
            parseMode = ParseMode.HTML
            chatId = update.senderId
            text = """
                <u><b>Статистика за неделю:</b></u>

                Мемов отправлено: <b>1</b>
                Прошло модерацию: <b>1</b>
                Получено: <b>💎 1 · 2 💩</b>

                Мемов оценено: <b>5</b>
                Поставлено: <b>💎 2 · 3 💩</b>

                Ранк за неделю: <b>#3</b>

                <u><b>Статистика за все время:</b></u>

                Мемов отправлено: <b>2</b>
                Прошло модерацию: <b>2</b>
                Получено: <b>💎 2 · 4 💩</b>

                Мемов оценено: <b>8</b>
                Поставлено: <b>💎 3 · 5 💩</b>

                Ранк: <b>#9</b>
            """.trimIndent()
        }

        commandHandler.handleSync(update)
        verifyBlocking(api) { executeSuspended(sendMessageMethod) }
        verifyBlocking(memeDao) { findAllBySenderId(777) }
        verifyBlocking(voteDao) { findAllByVoterId(777) }
        verifyBlocking(telegramUserDao) { findUserRank("777", 7) }
        verifyBlocking(telegramUserDao) { findUserRank("777") }
        verifyNoMoreInteractions(telegramUserDao, memeDao, voteDao, api)
    }
}
