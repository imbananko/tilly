package com.chsdngm.tilly

import com.chsdngm.tilly.config.TelegramConfig
import com.chsdngm.tilly.config.TelegramConfig.Companion.BETA_CHAT_ID
import com.chsdngm.tilly.config.TelegramConfig.Companion.CHANNEL_ID
import com.chsdngm.tilly.config.TelegramConfig.Companion.api
import com.chsdngm.tilly.publish.MemePublisher
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.utility.mention
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage
import org.telegram.telegrambots.meta.api.methods.send.SendMessage

@Service
@EnableScheduling
final class Schedulers(
    private val memeRepository: MemeRepository,
    private val memePublisher: MemePublisher,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // every hour since 8 am till 1 am Moscow time
    @Scheduled(cron = "0 0 5-22/1 * * *")
    private fun publishMeme() =
        runCatching {
            if (TelegramConfig.publishEnabled) {
                memePublisher.publishMemeIfSomethingExists()
            } else {
                log.info("meme publishing is disabled")
            }
        }.onFailure {
            SendMessage().apply {
                chatId = BETA_CHAT_ID
                text = it.format(update = null)
                parseMode = ParseMode.HTML
            }.let { method -> api.execute(method) }
        }

    @Scheduled(cron = "0 0 19 * * WED")
    private fun sendMemeOfTheWeek() =
        runCatching {
            memeRepository.findMemeOfTheWeek()?.let { meme ->
                val winner = api.execute(
                    GetChatMember(CHANNEL_ID, meme.senderId.toLong())
                ).user.mention()

                SendMessage().apply {
                    chatId = CHANNEL_ID
                    parseMode = ParseMode.HTML
                    replyToMessageId = meme.channelMessageId
                    text = "Поздравляем $winner с мемом недели!"
                }.let {
                    api.execute(it)
                }.also {
                    api.execute(PinChatMessage(it.chatId.toString(), it.messageId))
                }

                memeRepository.saveMemeOfWeek(meme.id)
            } ?: log.info("can't find meme of the week")
        }
            .onSuccess { log.info("successful send meme of the week") }
            .onFailure {
                log.error("can't send meme of the week because of", it)

                SendMessage().apply {
                    chatId = BETA_CHAT_ID
                    text = it.format(update = null)
                    parseMode = ParseMode.HTML
                }.let { method -> api.execute(method) }
            }
}



