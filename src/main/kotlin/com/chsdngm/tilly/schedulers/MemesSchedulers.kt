package com.chsdngm.tilly.schedulers

import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.config.MetadataProperties
import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.model.MemeStatus
import com.chsdngm.tilly.model.PrivateVoteValue
import com.chsdngm.tilly.model.dto.Meme
import com.chsdngm.tilly.model.dto.MemeLog
import com.chsdngm.tilly.model.dto.Vote
import com.chsdngm.tilly.repository.MemeDao
import com.chsdngm.tilly.repository.MemeLogDao
import com.chsdngm.tilly.repository.TelegramUserDao
import com.chsdngm.tilly.createMarkup
import com.chsdngm.tilly.format
import com.chsdngm.tilly.mention
import com.chsdngm.tilly.model.InstagramReelStatus
import com.chsdngm.tilly.model.dto.InstagramReel
import com.chsdngm.tilly.repository.InstagramReelDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember
import org.telegram.telegrambots.meta.api.methods.groupadministration.SetChatTitle
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.send.SendVideo
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Service
@EnableScheduling
class MemesSchedulers(
    private val memeDao: MemeDao,
    private val reelDao: InstagramReelDao,
    private val telegramUserDao: TelegramUserDao,
    private val memeLogDao: MemeLogDao,
    private val api: TelegramApi,
    private val telegramProperties: TelegramProperties,
    private val metadata: MetadataProperties
) {
    companion object {
        const val TILLY_LOG = "tilly.log"
        var formatter: DateTimeFormatter =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
    }

    private val log = LoggerFactory.getLogger(javaClass)

    // every hour since 8 am till 1 am Moscow time
    @Scheduled(cron = "0 0 5-22/1 * * *")
    fun publishMeme() = runBlocking {
        if (!telegramProperties.publishingEnabled) {
            log.info("meme publishing is disabled")
            return@runBlocking
        }

        runCatching {
            if (sequenceOf(publishReelIfAny(), publishMemeIfAny()).any { it }) {
                decrementQueueSizeInTitle()
            }
        }.onFailure {
            SendMessage().apply {
                chatId = telegramProperties.logsChatId
                text = it.format()
                parseMode = ParseMode.HTML
            }.let { method -> api.executeSuspended(method) }
        }
    }

    private suspend fun decrementQueueSizeInTitle() {
        val currentSize = getCurrentQueueSizeFromChatTitle()
        updateLogChannelTitle(currentSize - 1)
    }

    private suspend fun getCurrentQueueSizeFromChatTitle(): Int {
        val title = GetChat().apply {
            chatId = telegramProperties.logsChatId
        }.let { method -> api.executeSuspended(method) }.title

        return try {
            Regex(".*queued:([^\\[]+)").find(title)?.groupValues?.last()?.trim()?.toInt() ?: 0
        } catch (ex: Exception) {
            log.error("Failed to decrement queue size in title, exception=", ex)
            0
        }
    }

    private suspend fun publishReelIfAny(): Boolean {
        val scheduledReels = reelDao.findAllByStatusOrderByCreated(InstagramReelStatus.SCHEDULED)

        if (scheduledReels.isEmpty()) {
            log.info("No reels to post")
            return false
        }

        val (reel, votes) = scheduledReels.entries.first()

        reel.channelMessageId = sendReelToChannel(reel, votes).messageId
        reel.status = InstagramReelStatus.PUBLISHED
        reel.published = Instant.now()

        reelDao.update(reel)
        api.updateStatsInSenderChat(reel, votes)

        return true
    }

    private suspend fun publishMemeIfAny(): Boolean {
        val scheduledMemes = memeDao.findAllByStatusOrderByCreated(MemeStatus.SCHEDULED)

        if (scheduledMemes.isEmpty()) {
            log.info("No memes to post")
            return false
        }

        val (meme, votes) = scheduledMemes.entries.first()

        meme.channelMessageId = sendMemeToChannel(meme, votes).messageId
        meme.status = MemeStatus.PUBLISHED
        meme.published = Instant.now()

        memeDao.update(meme)
        api.updateStatsInSenderChat(meme, votes)

        return true
    }

    private suspend fun updateLogChannelTitle(queueSize: Int) =
        SetChatTitle().apply {
            chatId = telegramProperties.logsChatId
            title = "$TILLY_LOG | queued: $queueSize [${metadata.commitSha}]"
        }.let { api.executeSuspended(it) }

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

    private suspend fun sendReelToChannel(reel: InstagramReel, votes: List<Vote>): Message {
        val sendVideo = SendVideo().apply {
            chatId = telegramProperties.targetChannelId
            video = InputFile(reel.fileId)
            replyMarkup = createMarkup(votes)
            parseMode = ParseMode.HTML
        }

        val message = api.executeSuspended(sendVideo)
        log.info("sent reel to channel. reel=$reel")

        return message
    }

    @Scheduled(cron = "0 0 19 * * WED")
    private fun sendMemeOfTheWeek() = runBlocking {
        runCatching {
            val meme = memeDao.findTopRatedMemeForLastWeek()
            if (meme == null) {
                log.info("can't find meme of the week")
                return@runCatching
            }

            val winner = GetChatMember().apply {
                chatId = telegramProperties.targetChannelId
                userId = meme.senderId
            }.let { api.executeSuspended(it) }.user

            val winnerMention = if (winner.id == telegramProperties.botId) "montorn" else winner.mention()

            val message = SendMessage().apply {
                chatId = telegramProperties.targetChannelId
                parseMode = ParseMode.HTML
                replyToMessageId = meme.channelMessageId
                text = "Поздравляем $winnerMention с мемом недели!"
            }.let { api.executeSuspended(it) }

            launch { api.executeSuspended(PinChatMessage(message.chatId.toString(), message.messageId)) }
            launch { memeDao.saveMemeOfTheWeek(meme.id) }

            log.info("successful sent meme of the week")
        }.onFailure { ex ->
            log.error("Failed to process meme of the week, exception=", ex)

            SendMessage().apply {
                chatId = telegramProperties.logsChatId
                text = ex.format()
                parseMode = ParseMode.HTML
            }.let { api.executeSuspended(it) }
        }
    }

    @Scheduled(cron = "0 0 8 * * *")
    private fun resurrectMemes() = runBlocking {
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

        runCatching {
            val moderators = telegramUserDao.findTopFiveSendersForLastWeek(telegramProperties.botId).iterator()
            val deadMemes = memeDao.findDeadMemes().iterator()

            while (deadMemes.hasNext() && moderators.hasNext()) {
                val meme = deadMemes.next()
                val moderator = moderators.next()

                launch { memeLogDao.insert(MemeLog.fromMeme(meme)) }

                val sentMessage = SendPhoto().apply {
                    chatId = moderator.id.toString()
                    photo = InputFile(meme.fileId)
                    caption =
                        "Настало время некромантии \uD83C\uDF83 \nДата отправки: ${formatter.format(meme.created)}"
                    parseMode = ParseMode.HTML
                    replyMarkup = createResurrectionMarkup()
                }.let { api.executeSuspended(it) }

                val updatedMeme = meme.copy(
                    moderationChatId = moderator.id,
                    moderationChatMessageId = sentMessage.messageId,
                    status = MemeStatus.RESURRECTION_ASKED,
                    created = Instant.now()
                )

                launch { memeDao.update(updatedMeme) }

                val sendPhoto = SendPhoto().apply {
                    chatId = telegramProperties.logsChatId
                    photo = InputFile(meme.fileId)
                    caption = "мем отправлен на воскрешение к ${moderator.mention()}"
                    parseMode = ParseMode.HTML
                    disableNotification = true
                }

                launch { api.executeSuspended(sendPhoto) }
            }

            log.info("successfully sent memes to resurrection")
        }.onFailure {
            log.error("failed to resurrect memes", it)

            SendMessage().apply {
                chatId = telegramProperties.logsChatId
                text = it.format()
                parseMode = ParseMode.HTML
            }.let { method -> api.executeSuspended(method) }
        }
    }

    private suspend fun removeMessageReplyMarkup(moderationChatId: String, moderationMessageId: Int?) {
        EditMessageReplyMarkup().apply {
            chatId = moderationChatId
            messageId = moderationMessageId
        }.let { api.executeSuspended(it) }
    }

    @Scheduled(cron = "0 */9 4-22 * * *")
    fun scheduleForChannel() = runBlocking {
        val memes = async { scheduleMemesIfAny() }
        val reels = async { scheduleReelsIfAny() }

        val queueSize = memes.await() + reels.await()

        if (queueSize > 0) {
            launch { updateLogChannelTitle(queueSize) }
        }
    }

    private fun CoroutineScope.scheduleReelsIfAny(): Int {
        val reels = reelDao.scheduleReels()

        if (reels.isEmpty()) {
            log.info("there was no reels to schedule")
            return 0
        }

        reels.map { launch { removeMessageReplyMarkup(it.moderationChatId.toString(), it.moderationChatMessageId) } }
        log.info("successfully scheduled reels: $reels")

        return reels.size
    }

    private fun CoroutineScope.scheduleMemesIfAny(): Int {
        val memes = memeDao.scheduleMemes()

        if (memes.isEmpty()) {
            log.info("there was no memes to schedule")
            return 0
        }

        memes.map { launch { removeMessageReplyMarkup(it.moderationChatId.toString(), it.moderationChatMessageId) } }
        log.info("successfully scheduled memes: $memes")

        return memes.size
    }

    @Scheduled(cron = "0 0 19 * * FRI")
    private fun sendHowToSearchMemesReminder() = runBlocking {
        runCatching {
            val gif = File("/opt/how_to_search_ex.gif") // TODO send telegram file id instead of real file

            SendPhoto().apply {
                chatId = telegramProperties.targetChannelId
                photo = InputFile(gif)
                parseMode = ParseMode.HTML
                caption = "Есть лёгкое наитие, что ты даже и не подозреваешь, какую силу ты можешь возыметь с нашим ботом"
            }.let { api.executeSuspended(it) }
        }.onFailure { ex ->
            log.error("Failed to send 'how to search memes example', exception=", ex)

            SendMessage().apply {
                chatId = telegramProperties.logsChatId
                text = ex.format()
                parseMode = ParseMode.HTML
            }.let { api.executeSuspended(it) }
        }
    }
}
