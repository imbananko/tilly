package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.exposed.MemeDao
import com.chsdngm.tilly.exposed.MemeEntity
import com.chsdngm.tilly.exposed.Vote
import com.chsdngm.tilly.exposed.VoteDao
import com.chsdngm.tilly.model.MemeStatus.MODERATION
import com.chsdngm.tilly.model.MemeStatus.SCHEDULED
import com.chsdngm.tilly.model.VoteUpdate
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHANNEL_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHAT_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.MODERATION_THRESHOLD
import com.chsdngm.tilly.utility.TillyConfig.Companion.api
import com.chsdngm.tilly.utility.createMarkup
import com.chsdngm.tilly.utility.updateStatsInSenderChat
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Service
class VoteHandler(val memeDao: MemeDao, val voteDao: VoteDao) : AbstractHandler<VoteUpdate> {
    private val log = LoggerFactory.getLogger(javaClass)
    var executor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun handle(update: VoteUpdate) {
        CompletableFuture.supplyAsync(
            {
                if (update.isOld) {
                    sendPopupNotification(update.callbackQueryId, "Мем слишком стар")
                    return@supplyAsync
                }

                val meme = when (update.isFrom) {
                    CHANNEL_ID -> memeDao.findMemeByChannelMessageId(update.messageId)
                    CHAT_ID -> memeDao.findMemeByModerationChatIdAndModerationChatMessageId(
                        CHAT_ID.toLong(),
                        update.messageId
                    )
                    else -> return@supplyAsync
                } ?: return@supplyAsync

                val vote = Vote(meme.id.value, update.voterId.toInt(), update.isFrom.toLong(), update.voteValue)

                if (meme.senderId == vote.voterId) {
                    sendPopupNotification(update.callbackQueryId, "Голосуй за других, а не за себя")
                    return@supplyAsync
                }

                lateinit var voteUpdate: Runnable
                meme.votes.firstOrNull { it.voterId == vote.voterId }?.let { found ->
                    if (meme.votes.removeIf { it.voterId == vote.voterId && it.value == vote.value }) {
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
                } ?: meme.votes.add(vote).also {
                    when (vote.value) {
                        VoteValue.UP -> "Вы обогатили этот мем ${VoteValue.UP.emoji}"
                        VoteValue.DOWN -> "Вы засрали этот мем ${VoteValue.DOWN.emoji}"
                    }.let { sendPopupNotification(update.callbackQueryId, it) }
                    voteUpdate = Runnable { voteDao.insert(vote) }
                }

                updateMarkup(meme)
                checkShipment(meme)
                updateStatsInSenderChat(meme)

                voteUpdate.run()

                meme.flush()
                log.info("processed vote update=$update")
            },
            executor
        ).thenAccept { log.info("processed vote update=$update") }
    }

    fun sendPopupNotification(userCallbackQueryId: String, popupText: String): Boolean =
        AnswerCallbackQuery().apply {
            cacheTime = 0
            callbackQueryId = userCallbackQueryId
            text = popupText
        }.let { api.execute(it) }

    private fun updateMarkup(memeEntity: MemeEntity) {
        memeEntity.channelMessageId?.let {
            EditMessageReplyMarkup().apply {
                chatId = CHANNEL_ID
                messageId = memeEntity.channelMessageId
                replyMarkup = createMarkup(memeEntity.votes.groupingBy { it.value }.eachCount())
            }.let { api.execute(it) }
        }

        if (memeEntity.moderationChatId.toString() == CHAT_ID) {
            EditMessageReplyMarkup().apply {
                chatId = CHAT_ID
                messageId = memeEntity.moderationChatMessageId
                replyMarkup = createMarkup(memeEntity.votes.groupingBy { it.value }.eachCount())
            }.let { api.execute(it) }
        }
    }

    private fun checkShipment(memeEntity: MemeEntity) {
        val values = memeEntity.votes.map { it.value }
        val isEnough = values.filter { it == VoteValue.UP }.size - values.filter { it == VoteValue.DOWN }.size >= MODERATION_THRESHOLD

        if (isEnough && memeEntity.status == MODERATION) {
            transaction {
                memeEntity.status = SCHEDULED
            }
        }
    }
}
