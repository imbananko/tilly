package com.chsdngm.tilly.schedulers

import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.config.Metadata.Companion.COMMIT_SHA
import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.metrics.AccumulatingAppender
import com.chsdngm.tilly.model.MemeStatus
import com.chsdngm.tilly.model.PrivateVoteValue
import com.chsdngm.tilly.model.dto.Meme
import com.chsdngm.tilly.model.dto.MemeLog
import com.chsdngm.tilly.model.dto.Vote
import com.chsdngm.tilly.repository.MemeDao
import com.chsdngm.tilly.repository.MemeLogDao
import com.chsdngm.tilly.repository.TelegramUserDao
import com.chsdngm.tilly.similarity.ElasticsearchService
import com.chsdngm.tilly.utility.createMarkup
import com.chsdngm.tilly.utility.format
import com.chsdngm.tilly.utility.mention
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.core.LogEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember
import org.telegram.telegrambots.meta.api.methods.groupadministration.SetChatTitle
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Service
@EnableScheduling
final class Schedulers(
    private val memeDao: MemeDao,
    private val telegramUserDao: TelegramUserDao,
    private val memeLogDao: MemeLogDao,
    private val elasticsearchService: ElasticsearchService,
    private val telegramProperties: TelegramProperties,
    private val api: TelegramApi,
) {
    companion object {
        const val TILLY_LOG = "tilly.log"
        var formatter: DateTimeFormatter =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
    }

    private val log = LoggerFactory.getLogger(javaClass)

    // every hour since 8 am till 1 am Moscow time
    @Scheduled(cron = "0 0 5-22/1 * * *")
    private fun publishMeme() = runCatching {
        publishMemeIfSomethingExists()
    }.onFailure {
        SendMessage().apply {
            chatId = telegramProperties.logsChatId
            text = it.format()
            parseMode = ParseMode.HTML
        }.let { method -> api.execute(method) }
    }

    private fun publishMemeIfSomethingExists() = runBlocking {
        if (!telegramProperties.publishingEnabled) {
            log.info("meme publishing is disabled")
        }

        val scheduledMemes = memeDao.findAllByStatusOrderByCreated(MemeStatus.SCHEDULED)

        if (scheduledMemes.isNotEmpty()) {
            val (meme, votes) = scheduledMemes.entries.first().toPair()

            meme.channelMessageId = sendMemeToChannel(meme, votes).messageId
            meme.status = MemeStatus.PUBLISHED

            memeDao.update(meme)
            launch { api.updateStatsInSenderChat(meme, votes) }
            launch { updateLogChannelTitle(scheduledMemes.size - 1) }
        } else {
            log.info("Nothing to post")
        }
    }

    private suspend fun updateLogChannelTitle(queueSize: Int) =
        SetChatTitle().apply {
            chatId = telegramProperties.logsChatId
            title = "$TILLY_LOG | queued: $queueSize [$COMMIT_SHA]"
        }.let { api.executeSuspended(it) }

    private suspend fun updateLogChannelTitle(): Boolean {
        val queueSize = memeDao.findAllByStatusOrderByCreated(MemeStatus.SCHEDULED).size

        return SetChatTitle().apply {
            chatId = telegramProperties.logsChatId
            title = "$TILLY_LOG | queued: $queueSize [$COMMIT_SHA]"
        }.let { api.executeSuspended(it) }
    }

    private suspend fun sendMemeToChannel(meme: Meme, votes: List<Vote>): Message {
        val sendPhoto = SendPhoto().apply {
            chatId = telegramProperties.targetChannelId
            photo = InputFile(meme.fileId)
            replyMarkup = createMarkup(votes)
            parseMode = ParseMode.HTML
            caption = meme.caption
        }

        val message = api.executeSuspended(sendPhoto)
        log.info("sent meme to channel. meme=$meme")

        return message
    }

    @Scheduled(cron = "0 0 19 * * WED")
    private fun sendMemeOfTheWeek() = runCatching {
        val meme = memeDao.findTopRatedMemeForLastWeek()
        if (meme == null) {
            log.info("can't find meme of the week")
            return@runCatching
        }

        val winner = GetChatMember().apply {
            chatId = telegramProperties.targetChannelId
            userId = meme.senderId
        }.let { api.execute(it) }.user.mention(telegramProperties.botId)

        SendMessage().apply {
            chatId = telegramProperties.targetChannelId
            parseMode = ParseMode.HTML
            replyToMessageId = meme.channelMessageId
            text = "Поздравляем $winner с мемом недели!"
        }.let {
            val message = api.execute(it)
            api.execute(PinChatMessage(message.chatId.toString(), message.messageId))
        }

        memeDao.saveMemeOfTheWeek(meme.id)
    }
        .onSuccess { log.info("successful send meme of the week") }
        .onFailure { ex ->
            log.error("Failed to process meme of the week, exception=", ex)

            SendMessage().apply {
                chatId = telegramProperties.logsChatId
                text = ex.format()
                parseMode = ParseMode.HTML
            }.let { api.execute(it) }
        }

    @Scheduled(cron = "0 0 8 * * *")
    private fun resurrectMemes() = runCatching {
        fun createResurrectionMarkup() = InlineKeyboardMarkup(
            listOf(
                listOf(InlineKeyboardButton("Воскресить ${PrivateVoteValue.APPROVE.emoji}").also {
                    it.callbackData = PrivateVoteValue.APPROVE.name
                }),
                listOf(InlineKeyboardButton("Похоронить ${PrivateVoteValue.DECLINE.emoji}").also {
                    it.callbackData = PrivateVoteValue.DECLINE.name
                })
            )
        )

        val moderators = telegramUserDao.findTopFiveSendersForLastWeek(telegramProperties.botId).iterator()
        val deadMemes = memeDao.findDeadMemes().iterator()

        while (deadMemes.hasNext() && moderators.hasNext()) {
            val meme = deadMemes.next()
            val moderator = moderators.next()

            memeLogDao.insert(MemeLog.fromMeme(meme))

            val sentMessage = SendPhoto().apply {
                chatId = moderator.id.toString()
                photo = InputFile(meme.fileId)
                caption = "Настало время некромантии \uD83C\uDF83 \nДата отправки: ${formatter.format(meme.created)}"
                parseMode = ParseMode.HTML
                replyMarkup = createResurrectionMarkup()
            }.let { api.execute(it) }

            val updatedMeme = meme.copy(
                moderationChatId = moderator.id,
                moderationChatMessageId = sentMessage.messageId,
                status = MemeStatus.RESURRECTION_ASKED,
                created = Instant.now()
            )

            memeDao.update(updatedMeme)

            SendPhoto().apply {
                chatId = telegramProperties.logsChatId
                photo = InputFile(meme.fileId)
                caption = "мем отправлен на воскрешение к ${moderator.mention()}"
                parseMode = ParseMode.HTML
                disableNotification = true
            }.let { api.execute(it) }
        }
    }.onSuccess {
        log.info("successfully sent memes to resurrection")
    }.onFailure {
        log.error("failed to resurrect memes", it)
    }

    @Scheduled(cron = "0 */9 4-22 * * *")
    private fun checkMemesForScheduling() {
        suspend fun editMessageReplyMarkup(meme: Meme) {
            EditMessageReplyMarkup().apply {
                chatId = meme.moderationChatId.toString()
                messageId = meme.moderationChatMessageId
            }.let { api.executeSuspended(it) }
        }

        runBlocking {
            val memes = memeDao.scheduleMemes()

            if (memes.isEmpty()) {
                log.info("there was nothing to schedule")
                return@runBlocking
            }

            log.info("successfully scheduled memes: $memes")
            launch { updateLogChannelTitle() }
            memes.map { launch { editMessageReplyMarkup(it) } }
        }
    }

    @Scheduled(cron = "0 * * * * *")
    private fun sendLogs() = runCatching {
        val logs = mutableListOf<LogEvent>()
        AccumulatingAppender.drain(logs)
        if (logs.isNotEmpty()) {
            runBlocking {
                elasticsearchService.bulkIndexLogs(logs)
            }
        }
    }.onFailure {
        SendMessage().apply {
            chatId = telegramProperties.logsChatId
            text = it.format()
            parseMode = ParseMode.HTML
        }.let { method -> api.execute(method) }
    }
}
