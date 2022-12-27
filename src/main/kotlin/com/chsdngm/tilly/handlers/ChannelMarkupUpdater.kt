package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.collections.ExtendedCopyOnWriteArrayList
import com.chsdngm.tilly.config.TelegramConfig
import com.chsdngm.tilly.model.dto.Meme
import com.chsdngm.tilly.model.dto.Vote
import com.chsdngm.tilly.utility.createMarkup
import com.google.common.util.concurrent.RateLimiter
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import java.util.concurrent.Executors

@Service
class ChannelMarkupUpdater {
    private val queue = ExtendedCopyOnWriteArrayList<Pair<Meme, List<Vote>>>()
    private val rateLimiter = RateLimiter.create(0.2)

    init {
        Executors.newSingleThreadExecutor().submit {
            while (true) {
                if (queue.isNotEmpty() && rateLimiter.tryAcquire()) {
                    val head = queue.removeAt(0)
                    val lastSimilarToHead = queue.dropIf { it.first.id == head.first.id }
                    if (lastSimilarToHead == null) {
                        updateChannelMarkup(head.first, head.second)
                    }
                    if (lastSimilarToHead.second != head.second) {
                        updateChannelMarkup(lastSimilarToHead.first, lastSimilarToHead.second)
                    }
                }
            }
        }
    }

    fun submitVote(memeWithVotes: Pair<Meme, List<Vote>>) {
        queue.add(memeWithVotes)
    }

    private final fun updateChannelMarkup(meme: Meme, votes: List<Vote>) {
        EditMessageReplyMarkup().apply {
            chatId = TelegramConfig.CHANNEL_ID
            messageId = meme.channelMessageId
            replyMarkup = createMarkup(votes)
        }.let { TelegramConfig.api.executeAsync(it) }
    }
}