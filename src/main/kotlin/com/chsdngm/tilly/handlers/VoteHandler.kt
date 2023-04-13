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
import java.util.concurrent.CompletableFuture
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

        lateinit var voteDatabaseUpdate: CompletableFuture<*>
        votes.firstOrNull { it.voterId == vote.voterId }?.let { found ->
            if (votes.removeIf { it.voterId == vote.voterId && it.value == vote.value }) {
                sendPopupNotification(update.callbackQueryId, "Вы удалили свой голос с этого мема")
                voteDatabaseUpdate = CompletableFuture.supplyAsync { voteDao.delete(found) }
            } else {
                found.value = vote.value
                found.sourceChatId = vote.sourceChatId
                voteDatabaseUpdate = CompletableFuture.supplyAsync { voteDao.update(found) }

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
            voteDatabaseUpdate = CompletableFuture.supplyAsync { voteDao.insert(vote) }
        }

        var groupMarkupUpdate = CompletableFuture.completedFuture<Serializable>(null)
        if (meme.channelMessageId != null) {
            channelMarkupUpdater.submitVote(meme to votes)
        } else {
            updateGroupMarkup(meme, votes)
        }

        launch { api.updateStatsInSenderChat(meme, votes) }

        voteDatabaseUpdate.join()
//        CompletableFuture.allOf(voteDatabaseUpdate, popupNotification, groupMarkupUpdate)
        log.info("processed vote update=$update")
    }

    private fun sendPopupNotification(userCallbackQueryId: String, popupText: String): Boolean =
        AnswerCallbackQuery().apply {
            callbackQueryId = userCallbackQueryId
            text = popupText
        }.let { api.execute(it) }

    private fun updateGroupMarkup(meme: Meme, votes: List<Vote>): CompletableFuture<Serializable> =
        EditMessageReplyMarkup().apply {
            chatId = telegramProperties.targetChatId
            messageId = meme.moderationChatMessageId
            replyMarkup = createMarkup(votes)
        }.let { api.executeAsync(it) }

    override fun measureTime(update: VoteUpdate) {
        metricsUtils.measureDuration(update)
    }

    override fun retrieveSubtype(update: Update) =
        if (canHandle(update)) VoteUpdate(update) else null

    override fun canHandle(update: Update): Boolean {
       return update.hasCallbackQuery()
               && (update.callbackQuery.message.isSuperGroupMessage || update.callbackQuery.message.isChannelMessage)
               && VoteValue.values().map { it.name }.contains(update.callbackQuery.data)
    }
}
