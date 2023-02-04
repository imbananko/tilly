package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.config.TelegramConfig
import com.chsdngm.tilly.metrics.MetricsUtils
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
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import java.time.Instant

class CommandHandlerTest {
    private val telegramUserDao = mock(TelegramUserDao::class.java)
    private val memeDao = mock(MemeDao::class.java)
    private val voteDao = mock(VoteDao::class.java)
    private val api = mock(DefaultAbsSender::class.java)

    private val commandHandler = CommandHandler(telegramUserDao, memeDao, voteDao, mock(MetricsUtils::class.java), api)

    @Test
    fun shouldSendInfoMessageWhenHelpOrStartCommandReceived() {
        val update = mock(CommandUpdate::class.java).apply {
            `when`(value).thenReturn(CommandUpdate.Command.HELP)
            `when`(messageId).thenReturn(666)
            `when`(senderId).thenReturn("senderId")
        }

        val sendMessageMethod = SendMessage().apply {
            chatId = update.senderId
            parseMode = ParseMode.HTML
            //TODO: fix the static mocking for bot_name in text
            text = """
                Привет, я ${TelegramConfig.BOT_USERNAME}. 

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
        `when`(update.value).thenReturn(CommandUpdate.Command.START)
        commandHandler.handleSync(update)

        verify(api, times(2)).execute(sendMessageMethod)
        verifyNoMoreInteractions(telegramUserDao, memeDao, voteDao, api)
    }

    @Test
    fun shouldEnablePublicationWhenConfigCommandReceived() {
        val update = mock(CommandUpdate::class.java).apply {
            `when`(value).thenReturn(CommandUpdate.Command.CONFIG)
            `when`(messageId).thenReturn(666)
            //TODO fix the static mocking properties
            `when`(chatId).thenReturn(TelegramConfig.BETA_CHAT_ID)
            `when`(text).thenReturn("enable publishing")
        }

        commandHandler.handleSync(update)

        val sendMessageMethod = SendMessage().apply {
            chatId = TelegramConfig.BETA_CHAT_ID
            parseMode = ParseMode.HTML
            replyToMessageId = update.messageId
            text = "Публикация мемов включена"
        }

        verify(api).execute(sendMessageMethod)
        verifyNoMoreInteractions(telegramUserDao, memeDao, voteDao, api)
    }

    @Test
    fun shouldDisablePublicationWhenConfigCommandReceived() {
        val update = mock(CommandUpdate::class.java).apply {
            `when`(value).thenReturn(CommandUpdate.Command.CONFIG)
            `when`(messageId).thenReturn(666)
            //TODO fix the static mocking properties
            `when`(chatId).thenReturn(TelegramConfig.BETA_CHAT_ID)
            `when`(text).thenReturn("disable publishing")
        }

        `when`(update.value).thenReturn(CommandUpdate.Command.CONFIG)
        `when`(update.messageId).thenReturn(666)
        //TODO fix the static mocking properties
        `when`(update.chatId).thenReturn(TelegramConfig.BETA_CHAT_ID)
        `when`(update.text).thenReturn("disable publishing")

        commandHandler.handleSync(update)

        val sendMessageMethod = SendMessage().apply {
            chatId = TelegramConfig.BETA_CHAT_ID
            parseMode = ParseMode.HTML
            replyToMessageId = update.messageId
            text = "Публикация мемов выключена"
        }

        verify(api).execute(sendMessageMethod)
        verifyNoMoreInteractions(telegramUserDao, memeDao, voteDao, api)
    }

    @Test
    fun shouldDoNothingOnUnknownCommand() {
        val update = mock(CommandUpdate::class.java)

        `when`(update.value).thenReturn(null)
        commandHandler.handleSync(update)
        verifyNoMoreInteractions(telegramUserDao, memeDao, voteDao, api)
    }

    @Test
    fun shouldSendZeroStatsMessageOnStatsCommandWhenNoData() {
        val update = mock(CommandUpdate::class.java).apply {
            `when`(value).thenReturn(CommandUpdate.Command.STATS)
            `when`(messageId).thenReturn(666)
            `when`(senderId).thenReturn("777")
        }

        `when`(memeDao.findAllBySenderId(777)).thenReturn(mapOf())
        `when`(voteDao.findAllByVoterId(777)).thenReturn(listOf())

        val sendMessageMethod = SendMessage().apply {
            parseMode = ParseMode.HTML
            chatId = update.senderId
            text = "Статистика недоступна. Отправляй и оценивай мемы!"
        }

        commandHandler.handleSync(update)
        verify(api).execute(sendMessageMethod)
        verify(memeDao).findAllBySenderId(777)
        verify(voteDao).findAllByVoterId(777)
        verifyNoMoreInteractions(telegramUserDao, memeDao, voteDao, api)
    }

    @Test
    fun shouldSendWeekAndGlobalStatsMessageOnStatsCommand() {
        val update = mock(CommandUpdate::class.java).apply {
            `when`(value).thenReturn(CommandUpdate.Command.STATS)
            `when`(messageId).thenReturn(666)
            `when`(senderId).thenReturn("777")
        }

        val freshMeme = mock(Meme::class.java)
        `when`(freshMeme.created).thenReturn(Instant.now())

        val oldMeme = mock(Meme::class.java)
        `when`(oldMeme.created).thenReturn(Instant.now().minusDays(10)) // more than a week ago

        val freshLike = mock(Vote::class.java).apply {
            `when`(value).thenReturn(VoteValue.UP)
            `when`(created).thenReturn(Instant.now()) // more than a week ago
        }

        val freshDislike = mock(Vote::class.java).apply {
            `when`(value).thenReturn(VoteValue.DOWN)
            `when`(created).thenReturn(Instant.now()) // more than a week ago
        }

        val oldLike = mock(Vote::class.java).apply {
            `when`(value).thenReturn(VoteValue.UP)
            `when`(created).thenReturn(Instant.now().minusDays(10)) // more than a week ago
        }

        val oldDislike = mock(Vote::class.java).apply {
            `when`(value).thenReturn(VoteValue.DOWN)
            `when`(created).thenReturn(Instant.now().minusDays(10)) // more than a week ago
        }

        val memesWithVotes = mapOf(
                freshMeme to listOf(freshLike, freshLike, freshLike),
                freshMeme to listOf(freshLike, freshLike, freshDislike),
                freshMeme to listOf(freshLike, freshDislike, freshDislike),

                oldMeme to listOf(freshLike, freshLike, freshLike),
                oldMeme to listOf(freshLike, freshLike, freshDislike),
                oldMeme to listOf(freshLike, freshDislike, freshDislike)
        )
        `when`(memeDao.findAllBySenderId(777)).thenReturn(memesWithVotes)

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
        `when`(voteDao.findAllByVoterId(777)).thenReturn(votes)

        `when`(telegramUserDao.findUserRank("777")).thenReturn(9)

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

                Ранк за неделю: <b>#0</b>

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
        verify(api).execute(sendMessageMethod)
        verify(memeDao).findAllBySenderId(777)
        verify(voteDao).findAllByVoterId(777)
        verify(telegramUserDao).findUserRank("777", 7)
        verify(telegramUserDao).findUserRank("777")
        verifyNoMoreInteractions(telegramUserDao, memeDao, voteDao, api)
    }

}