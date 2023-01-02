package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.config.TelegramConfig
import com.chsdngm.tilly.model.dto.Meme
import com.chsdngm.tilly.model.dto.Vote
import com.chsdngm.tilly.utility.createMarkup
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import java.util.concurrent.TimeUnit

@Service
class ChannelMarkupUpdater {
    private val markupSubject = PublishSubject.create<Pair<Meme, List<Vote>>>()
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        val timeoutObservable = Flowable.interval(/* initialDelay = */ 0, /* period = */ 5, TimeUnit.SECONDS)
            .onBackpressureDrop()
            .toObservable()

        val markupObservable: Observable<Pair<Meme, List<Vote>>> = markupSubject
            .groupBy { it.first.id }
            .flatMap { it.throttleLatest(5, TimeUnit.SECONDS, /* emitLast = */ true) }

        timeoutObservable.zipWith(markupObservable) { _, m -> m }
            .subscribe {
                log.info("before update")
                updateChannelMarkup(it.first, it.second)
                log.info("after update")
            }
    }

    fun submitVote(memeWithVotes: Pair<Meme, List<Vote>>) {
        markupSubject.onNext(memeWithVotes)
    }

    private final fun updateChannelMarkup(meme: Meme, votes: List<Vote>) {
        EditMessageReplyMarkup().apply {
            chatId = TelegramConfig.CHANNEL_ID
            messageId = meme.channelMessageId
            replyMarkup = createMarkup(votes)
        }.let { TelegramConfig.api.execute(it) }
    }
}