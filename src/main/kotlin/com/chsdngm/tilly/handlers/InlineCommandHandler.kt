package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.model.InlineCommandUpdate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.Update
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

@Service
class InlineCommandHandler(val api: TelegramApi) : AbstractHandler<InlineCommandUpdate>() {
    private val log = LoggerFactory.getLogger(javaClass)

    //TODO remove
    @OptIn(ExperimentalTime::class)
    private val timeSource = TimeSource.Monotonic

    @OptIn(ExperimentalTime::class)
    override fun handleSync(update: InlineCommandUpdate) {
        log.info("processed inline command update=$update")
    }

    override fun retrieveSubtype(update: Update) =
        if (update.hasInlineQuery()) InlineCommandUpdate(update) else null
}
