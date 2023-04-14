package com.chsdngm.tilly.schedulers

import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.model.dto.Meme
import com.chsdngm.tilly.model.dto.Vote
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import org.springframework.stereotype.Service
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Service
class ChannelMarkupUpdater(private val api: TelegramApi) {
    private val markupSubject = PublishSubject.create<Pair<Meme, List<Vote>>>()

    init {
        Executors.newSingleThreadExecutor().submit {
            val timeoutObservable = Flowable.interval(/* initialDelay = */ 0, /* period = */ 5, TimeUnit.SECONDS)
                .onBackpressureDrop()
                .toObservable()

            val markupObservable: Observable<Pair<Meme, List<Vote>>> = markupSubject
                .groupBy { it.first.id }
                .flatMap { it.throttleLatest(5, TimeUnit.SECONDS, /* emitLast = */ true) }

            timeoutObservable.zipWith(markupObservable) { _, m -> m }
                .subscribe {
                    api.updateChannelMarkup(it.first, it.second)
                }
        }
    }

    suspend fun submitVote(memeWithVotes: Pair<Meme, List<Vote>>) {
        markupSubject.onNext(memeWithVotes)
    }
}