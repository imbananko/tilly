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
import com.chsdngm.tilly.utility.createMarkup
import javassist.NotFoundException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.objects.Update
import java.io.Serializable
import java.time.Instant
import java.util.concurrent.Executors

@Service
class VoteHandler(
    private val memeDao: MemeDao,
    private val voteDao: VoteDao,
    private val metricsUtils: MetricsUtils,
    private val channelMarkupUpdater: ChannelMarkupUpdater,
    private val api: TelegramApi,
    private val telegramProperties: TelegramProperties
) : AbstractHandler<VoteUpdate>(Executors.newSingleThreadExecutor()) {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun handleSync(update: VoteUpdate) = runBlocking {
        if (update.isOld) {
            sendPopupNotification(update.callbackQueryId, "Мем слишком стар")
            return@runBlocking
        }

        val memeWithVotes = when (update.sourceChatId) {
            telegramProperties.targetChannelId -> memeDao.findMemeByChannelMessageId(update.messageId)
            telegramProperties.targetChatId -> memeDao.findMemeByModerationChatIdAndModerationChatMessageId(
                telegramProperties.targetChatId.toLong(),
                update.messageId
            )

            else -> null
        } ?: throw NotFoundException("Meme wasn't found. update=$update")

        val meme = memeWithVotes.first
        val votes = memeWithVotes.second.toMutableList()

        if (meme.senderId == update.voterId) {
            sendPopupNotification(update.callbackQueryId, "Голосуй за других, а не за себя")
            return@runBlocking
        }

        val vote = Vote(
            meme.id,
            update.voterId,
            update.sourceChatId.toLong(),
            update.voteValue,
            created = Instant.ofEpochMilli(update.createdAt)
        )

        fun getUpdatedVoteText(vote: Vote) = when (vote.value) {
            VoteValue.UP -> "Вы обогатили этот мем ${VoteValue.UP.emoji}"
            VoteValue.DOWN -> "Вы засрали этот мем ${VoteValue.DOWN.emoji}"
        }

        //TODO refactor with hashset instead of list (after tests)
        votes.firstOrNull { it.voterId == vote.voterId }?.let { found ->
            if (votes.removeIf { it.voterId == vote.voterId && it.value == vote.value }) {
                launch { sendPopupNotification(update.callbackQueryId, "Вы удалили свой голос с этого мема") }
                launch { voteDao.delete(found) }
            } else {
                found.value = vote.value
                found.sourceChatId = vote.sourceChatId

                val text = getUpdatedVoteText(vote)
                launch { sendPopupNotification(update.callbackQueryId, text) }
                launch { voteDao.update(found) }
            }
        } ?: votes.add(vote).also {

            val text = getUpdatedVoteText(vote)
            launch { sendPopupNotification(update.callbackQueryId, text) }
            launch { voteDao.insert(vote) }
        }

        if (meme.channelMessageId != null) {
            launch { channelMarkupUpdater.submitVote(meme.channelMessageId!! to votes) }
        } else {
            launch { updateGroupMarkup(meme, votes) }
        }

        launch { api.updateStatsInSenderChat(meme, votes) }

        log.info("processed vote update=$update")
    }

    private suspend fun sendPopupNotification(userCallbackQueryId: String, popupText: String): Boolean =
        AnswerCallbackQuery().apply {
            callbackQueryId = userCallbackQueryId
            text = popupText
        }.let { api.executeSuspended(it) }

    private suspend fun updateGroupMarkup(meme: Meme, votes: List<Vote>): Serializable =
        EditMessageReplyMarkup().apply {
            chatId = telegramProperties.targetChatId
            messageId = meme.moderationChatMessageId
            replyMarkup = createMarkup(votes)
        }.let { api.executeSuspended(it) }

    override fun measureTime(update: VoteUpdate) {
        metricsUtils.measureDuration(update)
    }

    override fun retrieveSubtype(update: Update) =
        if (update.hasCallbackQuery()
            && (update.callbackQuery.message.isSuperGroupMessage || update.callbackQuery.message.isChannelMessage)
            && VoteValue.values().map { it.name }.contains(update.callbackQuery.data)
        ) {
            VoteUpdate(update)
        } else null
}
