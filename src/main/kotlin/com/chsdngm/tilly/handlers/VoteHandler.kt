package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.config.TelegramConfig.Companion.CHANNEL_ID
import com.chsdngm.tilly.config.TelegramConfig.Companion.CHAT_ID
import com.chsdngm.tilly.config.TelegramConfig.Companion.api
import com.chsdngm.tilly.metrics.MetricsUtils
import com.chsdngm.tilly.model.VoteUpdate
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.model.dto.Meme
import com.chsdngm.tilly.model.dto.Vote
import com.chsdngm.tilly.repository.MemeDao
import com.chsdngm.tilly.repository.VoteDao
import com.chsdngm.tilly.utility.createMarkup
import com.chsdngm.tilly.utility.updateStatsInSenderChat
import javassist.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Service
class VoteHandler(
    val memeDao: MemeDao,
    val voteDao: VoteDao,
    val metricsUtils: MetricsUtils,
    val markupUtils: MarkupUtils
) : AbstractHandler<VoteUpdate>() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val voteExecutorService = Executors.newSingleThreadExecutor()

    override fun handleSync(update: VoteUpdate) {
//        if (update.isOld) {
//            sendPopupNotification(update.callbackQueryId, "Мем слишком стар")
//            return
//        }

        val memeWithVotes = when (update.sourceChatId) {
            CHANNEL_ID -> memeDao.findMemeByChannelMessageId(update.messageId)
            CHAT_ID -> memeDao.findMemeByModerationChatIdAndModerationChatMessageId(
                CHAT_ID.toLong(),
                update.messageId
            )
            else -> null
        } ?: throw NotFoundException("Meme wasn't found. update=$update")

        val meme = memeWithVotes.first
        val votes = memeWithVotes.second.toMutableList()

//        if (meme.senderId == update.voterId) {
//            sendPopupNotification(update.callbackQueryId, "Голосуй за других, а не за себя")
//            return
//        }

        val vote = Vote(
            meme.id,
            update.voterId,
            update.sourceChatId.toLong(),
            update.voteValue,
            created = Instant.ofEpochMilli(update.createdAt)
        )

        markupUtils.submitVote(meme)

        lateinit var voteUpdate: Runnable
        votes.firstOrNull { it.voterId == vote.voterId }?.let { found ->
            if (votes.removeIf { it.voterId == vote.voterId && it.value == vote.value }) {
                sendPopupNotification(update.callbackQueryId, "Вы удалили свой голос с этого мема")
                voteUpdate = Runnable { voteDao.delete(found) }
            } else {
                found.value = vote.value
                found.sourceChatId = vote.sourceChatId
                voteUpdate = Runnable { voteDao.update(found) }

                when (vote.value) {
                    VoteValue.UP -> "Вы обогатили этот мем ${VoteValue.UP.emoji}"
                    VoteValue.DOWN -> "Вы засрали этот мем ${VoteValue.DOWN.emoji}"
                }.let { sendPopupNotification(update.callbackQueryId, it) }
            }
        } ?: votes.add(vote).also {
            when (vote.value) {
                VoteValue.UP -> "Вы обогатили этот мем ${VoteValue.UP.emoji}"
                VoteValue.DOWN -> "Вы засрали этот мем ${VoteValue.DOWN.emoji}"
            }.let { sendPopupNotification(update.callbackQueryId, it) }
            voteUpdate = Runnable { voteDao.insert(vote) }
        }

        val markupUpdate = updateMarkup(meme, votes)
        updateStatsInSenderChat(meme, votes)

        voteUpdate.run()

        markupUpdate.join()
//        log.info("processed vote update=$update")
    }

    fun sendPopupNotification(userCallbackQueryId: String, popupText: String): Boolean =
        AnswerCallbackQuery().apply {
            cacheTime = 0
            callbackQueryId = userCallbackQueryId
            text = popupText
        }.let { api.execute(it) }

    private fun updateMarkup(meme: Meme, votes: List<Vote>): CompletableFuture<Void> {
        val channelUpdate = if (meme.channelMessageId != null) {
            EditMessageReplyMarkup().apply {
                chatId = CHANNEL_ID
                messageId = meme.channelMessageId
                replyMarkup = createMarkup(votes)
            }.let { api.executeAsync(it) }
        } else {
            CompletableFuture.completedFuture(null)
        }

        val groupUpdate = if (meme.moderationChatId.toString() == CHAT_ID) {
            EditMessageReplyMarkup().apply {
                chatId = CHAT_ID
                messageId = meme.moderationChatMessageId
                replyMarkup = createMarkup(votes)
            }.let { api.executeAsync(it) }
        } else {
            CompletableFuture.completedFuture(null)
        }

        return CompletableFuture.allOf(channelUpdate, groupUpdate)
    }

    override fun measureTime(update: VoteUpdate) {
        metricsUtils.measure(update)
    }

    override fun getExecutor(): ExecutorService = voteExecutorService
}
