package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.*
import com.chsdngm.tilly.model.VoteUpdateType.ADD_NEW_VALUE
import com.chsdngm.tilly.model.VoteUpdateType.REMOVE_EXISTING
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.utility.TillyConfig
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHANNEL_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHAT_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.MODERATION_THRESHOLD
import com.chsdngm.tilly.utility.TillyConfig.Companion.api
import com.chsdngm.tilly.utility.createMarkup
import com.chsdngm.tilly.utility.updateStatsInSenderChat
import com.google.common.collect.LinkedHashMultimap
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.LinkedMultiValueMap
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write


@Service
class VoteHandler(
    private val memeRepository: MemeRepository
) : AbstractHandler<VoteUpdate> {
    private val log = LoggerFactory.getLogger(javaClass)
    private val lock = ReentrantReadWriteLock()

    val map = LinkedMultiValueMap<Meme, Vote>()

    // Initial meme state with all updates to process
    val pendingEventsByMeme: LinkedHashMultimap<Meme, VoteUpdate> = LinkedHashMultimap.create()

    init {
        val executor = Executors.newScheduledThreadPool(2)
        executor.scheduleAtFixedRate(dumpPendingEvents(), 0, 5, TimeUnit.SECONDS)
    }

    private fun dumpPendingEvents() = Runnable {
        log.info("dumpPendingEvents")
        try {
            lock.write {
                val meme = pendingEventsByMeme.keySet().firstOrNull()

                if (meme != null) {
                    val votesUpdatesToProcess = pendingEventsByMeme.get(meme)

                    val votesBeforeUpdate = HashSet(meme.votes)

                    for (voteUpdate in votesUpdatesToProcess) {
                        val vote = Vote(
                            meme.id,
                            voteUpdate.voterId.toInt(),
                            voteUpdate.isFrom.toLong(),
                            voteUpdate.voteValue
                        )

                        if (voteUpdate.type == ADD_NEW_VALUE) {
                            meme.votes.remove(vote)
                            meme.votes.add(vote)
                        } else if (voteUpdate.type == REMOVE_EXISTING) {
                            meme.votes.remove(vote)
                        }
                    }

                    if (votesBeforeUpdate != meme.votes) {
                        updateMarkup(meme)

                        if (meme.status.canBeScheduled() && readyForShipment(meme)) {
                            meme.status = MemeStatus.SCHEDULED
                        }
                        memeRepository.save(meme)
                        updateStatsInSenderChat(meme)
                    }

                    pendingEventsByMeme.removeAll(meme)
                }
            }
        } catch (e: Exception) {
            log.error("Error during dumping updates", e)
            SendMessage().apply {
                chatId = TillyConfig.BETA_CHAT_ID
                text = "message: ${e.message}\ncause: ${e.cause}"
                parseMode = ParseMode.HTML
            }.let { method -> api.execute(method) }
        }
    }

    @Transactional(readOnly = true)
    override fun handle(update: VoteUpdate) {
        if (update.isOld) {
            sendPopupNotification(update.callbackQueryId, "Мем слишком стар")
            return
        }

        val meme = when (update.isFrom) {
            CHANNEL_ID -> memeRepository.findMemeByChannelMessageId(update.messageId)
            CHAT_ID -> memeRepository.findMemeByModerationChatIdAndModerationChatMessageId(
                CHAT_ID.toLong(),
                update.messageId
            )
            else -> return
        } ?: return

        if (meme.senderId == update.voterId.toInt()) {
            sendPopupNotification(update.callbackQueryId, "Голосуй за других, а не за себя")
            return
        }

        // TODO check if we have pending events with same meme id -> no need to go to database

        var popupNotification = createPopupNotification(update.callbackQueryId, "Голос обрабатывается")

        val pendingEvents: MutableSet<VoteUpdate>
        lock.read {
            pendingEvents = pendingEventsByMeme[meme] ?: mutableSetOf()
        }

        val pendingEventByUser = pendingEvents.firstOrNull { it.voterId == update.voterId }

        // Zero pending votes by this user
        if (pendingEventByUser == null) {

            // Checking if meme has persisted vote by this user
            val persistedVoteByUser =
                meme.votes.firstOrNull { it.voterId == update.voterId.toInt() && it.value == update.voteValue }

            popupNotification = if (persistedVoteByUser != null) {
                // We already have persisted vote by this user
                update.apply { type = REMOVE_EXISTING }
                createPopupNotification(update.callbackQueryId, "Вы удалили свой голос с этого мема")
            } else {
                update.apply { type = ADD_NEW_VALUE }
                createPopupNotification(
                    update.callbackQueryId,
                    when (update.voteValue) {
                        VoteValue.UP -> "Вы обогатили этот мем ${VoteValue.UP.emoji}"
                        VoteValue.DOWN -> "Вы засрали этот мем ${VoteValue.DOWN.emoji}"
                    }
                )
            }

            lock.write {
                pendingEventsByMeme[meme].add(update)
            }
        }

        // Have pending votes by user
        if (pendingEventByUser != null) {

            // Same vote, should be removed
            if (pendingEventByUser.voteValue == update.voteValue) {
                popupNotification =
                    createPopupNotification(update.callbackQueryId, "Вы удалили свой голос с этого мема")

                lock.write {
                    pendingEventsByMeme.remove(meme, pendingEventByUser)
                }
            } else {
                popupNotification = createPopupNotification(
                    update.callbackQueryId,
                    when (update.voteValue) {
                        VoteValue.UP -> "Вы обогатили этот мем ${VoteValue.UP.emoji}"
                        VoteValue.DOWN -> "Вы засрали этот мем ${VoteValue.DOWN.emoji}"
                    }
                )

                lock.write {
                    pendingEventsByMeme.remove(meme, pendingEventByUser)
                    pendingEventsByMeme[meme].add(update.apply { type = ADD_NEW_VALUE })
                }
            }
        }

        sendPopupNotification(popupNotification)
        log.info("processed vote update=$update")
    }

    fun createPopupNotification(userCallbackQueryId: String, popupText: String) =
        AnswerCallbackQuery().apply {
            cacheTime = 0
            callbackQueryId = userCallbackQueryId
            text = popupText
        }

    fun sendPopupNotification(popupNotification: AnswerCallbackQuery?): Boolean =
        api.execute(popupNotification)

    fun sendPopupNotification(userCallbackQueryId: String, popupText: String): Boolean =
        AnswerCallbackQuery().apply {
            cacheTime = 0
            callbackQueryId = userCallbackQueryId
            text = popupText
        }.let { api.execute(it) }

    private fun updateMarkup(meme: Meme) {
        meme.channelMessageId?.let {
            EditMessageReplyMarkup().apply {
                chatId = CHANNEL_ID
                messageId = meme.channelMessageId
                replyMarkup = createMarkup(meme.votes.groupingBy { it.value }.eachCount())
            }.let { api.execute(it) }
        }

        if (meme.moderationChatId.toString() == CHAT_ID) {
            EditMessageReplyMarkup().apply {
                chatId = CHAT_ID
                messageId = meme.moderationChatMessageId
                replyMarkup = createMarkup(meme.votes.groupingBy { it.value }.eachCount())
            }.let { api.execute(it) }
        }
    }

    private fun readyForShipment(meme: Meme): Boolean =
        with(meme.votes.map { it.value }) {
            this.filter { it == VoteValue.UP }.size - this.filter { it == VoteValue.DOWN }.size >= MODERATION_THRESHOLD
        }

}
