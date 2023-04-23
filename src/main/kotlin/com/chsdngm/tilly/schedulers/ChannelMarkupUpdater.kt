package com.chsdngm.tilly.schedulers

import collections.ExtendedCopyOnWriteArrayList
import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.model.dto.Vote
import com.chsdngm.tilly.utility.createMarkup
import com.google.common.util.concurrent.RateLimiter
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

@Service
class ChannelMarkupUpdater(
    private val api: TelegramApi,
    private val properties: TelegramProperties,
) {

    private val queue = ExtendedCopyOnWriteArrayList<Pair<Int, List<Vote>>>()
    private val rateLimiter = RateLimiter.create(0.2)

    private val log = LoggerFactory.getLogger(javaClass)

    @OptIn(ExperimentalTime::class)
    //TODO remove
    private val timeSource = TimeSource.Monotonic

    init {
        runBlocking {
            launch(Job()) {
                while (true) {
                    delay(50)
                    if (queue.isNotEmpty() && withContext(Dispatchers.IO) { rateLimiter.tryAcquire() }) {
                        val messageId: Int
                        var votes: List<Vote>
                        queue.removeFirst().also {
                            messageId = it.first
                            votes = it.second
                        }

                        val lastSimilarToHead = queue.dropIf({ it.first == messageId }) {
                            if (it > 1) log.info("Suppressed votes count: $it")
                        }

                        if (lastSimilarToHead != null) {
                            votes = lastSimilarToHead.second
                        }

                        //TODO check logs for errors like: keyboard markup is the same
                        updateChannelMarkup(messageId, votes)
                    }
                }
            }
        }
    }

    fun submitVote(memeWithVotes: Pair<Int, List<Vote>>) {
        queue.add(memeWithVotes)
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun updateChannelMarkup(channelMessageId: Int, votes: List<Vote>) {
        val mark = timeSource.markNow()
        try {
            EditMessageReplyMarkup().apply {
                chatId = properties.targetChannelId
                messageId = channelMessageId
                replyMarkup = createMarkup(votes)
            }.let { api.executeSuspended(it) }
        } catch (e: Exception) {
            log.error("Failed to update markup", e)
        }

        log.info("updateChannelMarkup elapsed ${mark.elapsedNow()}")
    }
}