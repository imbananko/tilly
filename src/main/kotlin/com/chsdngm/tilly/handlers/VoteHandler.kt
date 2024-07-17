package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.createMarkup
import com.chsdngm.tilly.metrics.MetricsUtils
import com.chsdngm.tilly.model.VideoVoteUpdate
import com.chsdngm.tilly.model.VoteUpdate
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.model.dto.Vote
import com.chsdngm.tilly.repository.InstagramReelDao
import com.chsdngm.tilly.repository.MemeDao
import com.chsdngm.tilly.repository.VoteDao
import com.chsdngm.tilly.schedulers.ChannelMarkupUpdater
import javassist.NotFoundException
import kotlinx.coroutines.CoroutineScope
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
    private val instagramReelDao: InstagramReelDao,
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

        when (update) {
            is VideoVoteUpdate -> handleVideoVoteUpdate(update)
            else -> handleMemeVoteUpdate(update)
        }

        log.info("processed vote update=$update")
    }

    private fun CoroutineScope.handleMemeVoteUpdate(update: VoteUpdate) {
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
            launch { sendPopupNotification(update.callbackQueryId, "Голосуй за других, а не за себя") }
            return
        }

        val vote = Vote(
            meme.id,
            update.voterId,
            update.sourceChatId.toLong(),
            update.voteValue,
            created = Instant.ofEpochMilli(update.createdAt)
        )

        processVote(votes, vote, update)

        if (meme.channelMessageId != null) {
            launch { channelMarkupUpdater.submitVote(meme.channelMessageId!! to votes) }
        } else {
            launch { updateGroupMarkup(meme.moderationChatMessageId, votes) }
        }

        launch { api.updateStatsInSenderChat(meme, votes) }
    }

    private fun CoroutineScope.processVote(
        existingVotes: MutableList<Vote>,
        newVote: Vote,
        update: VoteUpdate
    ) {
        fun getUpdatedVoteText(vote: Vote) = when (vote.value) {
            VoteValue.UP -> "Вы обогатили этот мем ${VoteValue.UP.emoji}"
            VoteValue.DOWN -> "Вы засрали этот мем ${VoteValue.DOWN.emoji}"
        }

        //TODO refactor with hashset instead of list (after tests)
        existingVotes.firstOrNull { it.voterId == newVote.voterId }?.let { found ->
            if (existingVotes.removeIf { it.voterId == newVote.voterId && it.value == newVote.value }) {
                launch { sendPopupNotification(update.callbackQueryId, "Вы удалили свой голос с этого мема") }
                launch { voteDao.delete(found) }
            } else {
                found.value = newVote.value
                found.sourceChatId = newVote.sourceChatId

                val text = getUpdatedVoteText(newVote)
                launch { sendPopupNotification(update.callbackQueryId, text) }
                launch { voteDao.update(found) }
            }
        } ?: existingVotes.add(newVote).also {

            val text = getUpdatedVoteText(newVote)
            launch { sendPopupNotification(update.callbackQueryId, text) }
            launch { voteDao.insert(newVote) }
        }
    }

    private fun CoroutineScope.handleVideoVoteUpdate(update: VideoVoteUpdate) {
        val reelWithVotes = when (update.sourceChatId) {
            telegramProperties.targetChannelId -> instagramReelDao.findReelByChannelMessageId(update.messageId)
            telegramProperties.targetChatId -> instagramReelDao.findReelByModerationChatIdAndModerationChatMessageId(
                telegramProperties.targetChatId.toLong(),
                update.messageId
            )

            else -> null
        } ?: throw NotFoundException("Meme wasn't found. update=$update")

        val reel = reelWithVotes.first
        val votes = reelWithVotes.second.toMutableList()

        if (reel.senderId == update.voterId) {
            launch { sendPopupNotification(update.callbackQueryId, "Голосуй за других, а не за себя") }
            return
        }

        val vote = Vote(
            reel.id,
            update.voterId,
            update.sourceChatId.toLong(),
            update.voteValue,
            created = Instant.ofEpochMilli(update.createdAt)
        )

        processVote(votes, vote, update)

        if (reel.channelMessageId != null) {
            launch { channelMarkupUpdater.submitVote(reel.channelMessageId!! to votes) }
        } else {
            launch { updateGroupMarkup(reel.moderationChatMessageId, votes) }
        }

        launch { api.updateStatsInSenderChat(reel, votes) }
    }

    private suspend fun sendPopupNotification(userCallbackQueryId: String, popupText: String): Boolean =
        AnswerCallbackQuery().apply {
            callbackQueryId = userCallbackQueryId
            text = popupText
        }.let { api.executeSuspended(it) }

    private suspend fun updateGroupMarkup(moderationChatMessageId: Int?, votes: List<Vote>): Serializable =
        EditMessageReplyMarkup().apply {
            chatId = telegramProperties.targetChatId
            messageId = moderationChatMessageId
            replyMarkup = createMarkup(votes)
        }.let { api.executeSuspended(it) }

    override fun measureTime(update: VoteUpdate) {
        metricsUtils.measureDuration(update)
    }

    override fun retrieveSubtype(update: Update): VoteUpdate? {
        if (!update.hasCallbackQuery()) return null
        if (!(update.callbackQuery.message.isSuperGroupMessage || update.callbackQuery.message.isChannelMessage)) return null
        if (!VoteValue.values().map { it.name }.contains(update.callbackQuery.data)) return null

        if (update.callbackQuery.message.hasVideo()) {
            return VideoVoteUpdate(update)
        }

        return VoteUpdate(update)
    }
}
