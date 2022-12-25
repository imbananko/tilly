package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.dto.Meme
import com.google.common.util.concurrent.RateLimiter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

@Service
class MarkupUtils {
    private val log = LoggerFactory.getLogger(javaClass)

    private val queue = CopyOnWriteArrayList<Meme>()
    private val rateLimiter = RateLimiter.create(0.2)

    init {
        Executors.newSingleThreadExecutor().submit {
            while (true) {
                if (queue.isNotEmpty() && rateLimiter.tryAcquire()) {
                    calculateAndMerge()
                    editMarkup()
                }
            }
        }
    }

    fun submitVote(meme: Meme) {
        log.info("trying to put vote to queue")
        queue.add(meme)
        log.info("success putting vote to queue")
    }

    private final fun calculateAndMerge() {
        log.info("starting calculateAndMerge")
        val memeToUpdate = queue.removeAt(0)
        val wasRemoved = queue.removeIf { it.id == memeToUpdate.id }
        log.info(
            "success calculateAndMerge, id={} to be updated. something was merged = {}",
            memeToUpdate?.id,
            wasRemoved
        )
    }

    private final fun editMarkup() {
        log.info("editMarkup")
    }

}