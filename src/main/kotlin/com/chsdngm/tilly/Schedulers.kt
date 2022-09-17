package com.chsdngm.tilly

import com.chsdngm.tilly.config.Metadata.Companion.COMMIT_SHA
import com.chsdngm.tilly.config.TelegramConfig
import com.chsdngm.tilly.config.TelegramConfig.Companion.BETA_CHAT_ID
import com.chsdngm.tilly.config.TelegramConfig.Companion.CHANNEL_ID
import com.chsdngm.tilly.config.TelegramConfig.Companion.LOGS_CHAT_ID
import com.chsdngm.tilly.config.TelegramConfig.Companion.api
import com.chsdngm.tilly.model.MemeStatus
import com.chsdngm.tilly.model.dto.Meme
import com.chsdngm.tilly.repository.MemeDao
import com.chsdngm.tilly.utility.createMarkup
import com.chsdngm.tilly.utility.mention
import com.chsdngm.tilly.utility.updateStatsInSenderChat
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember
import org.telegram.telegrambots.meta.api.methods.groupadministration.SetChatTitle
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile

@Service
@EnableScheduling
final class Schedulers(private val memeDao: MemeDao) {
    companion object {
        const val TILLY_LOG = "tilly.log"
    }

    private val log = LoggerFactory.getLogger(javaClass)

    // every hour since 8 am till 1 am Moscow time
    @Scheduled(cron = "0 0 5-22/1 * * *")
    private fun publishMeme() = runCatching {
        publishMemeIfSomethingExists()
    }.onFailure {
        SendMessage().apply {
            chatId = BETA_CHAT_ID
            text = it.format(update = null)
            parseMode = ParseMode.HTML
        }.let { method -> api.execute(method) }
    }

    private fun publishMemeIfSomethingExists() {
        if (!TelegramConfig.publishEnabled) {
            log.info("meme publishing is disabled")
        }

        val memesToPublish = memeDao.findAllByStatusOrderByCreated(MemeStatus.SCHEDULED)

        if (memesToPublish.isNotEmpty()) {
            val memeToPublish = memesToPublish.first()
            memeToPublish.channelMessageId = sendMemeToChannel(memeToPublish).messageId
            memeToPublish.status = MemeStatus.PUBLISHED

            memeDao.update(memeToPublish)
            updateStatsInSenderChat(memeToPublish)
            updateLogChannelTitle(memesToPublish.size - 1)
        } else {
            log.info("Nothing to post")
        }
    }

    private fun updateLogChannelTitle(queueSize: Int) {
        SetChatTitle().apply {
            chatId = LOGS_CHAT_ID
            title = "$TILLY_LOG [$COMMIT_SHA] | queued: $queueSize"
        }.let { api.execute(it) }
    }

    private fun sendMemeToChannel(meme: Meme) = SendPhoto().apply {
        chatId = CHANNEL_ID
        photo = InputFile(meme.fileId)
        replyMarkup = createMarkup(meme.votes.groupingBy { it.value }.eachCount())
        parseMode = ParseMode.HTML
        caption = meme.caption
    }.let(api::execute).also { log.info("sent meme to channel. meme=$meme") }

    @Scheduled(cron = "0 0 19 * * WED")
    private fun sendMemeOfTheWeek() = runCatching {
        val meme = memeDao.findTopRatedMemeForLastWeek()
        if (meme == null) {
            log.info("can't find meme of the week")
            return@runCatching
        }

        val winner = GetChatMember().apply {
            chatId = CHANNEL_ID
            userId = meme.senderId.toLong()
        }.let { api.execute(it) }.user.mention()

        SendMessage().apply {
            chatId = CHANNEL_ID
            parseMode = ParseMode.HTML
            replyToMessageId = meme.channelMessageId
            text = "Поздравляем $winner с мемом недели!"
        }.let {
            val message = api.execute(it)
            api.execute(PinChatMessage(message.chatId.toString(), message.messageId))
        }

        memeDao.saveMemeOfTheWeek(meme.id)
    }
        .onSuccess { log.info("successful send meme of the week") }
        .onFailure { ex ->
            log.error("Failed to process meme of the week, exception=", ex)

            SendMessage().apply {
                chatId = BETA_CHAT_ID
                text = ex.format(update = null)
                parseMode = ParseMode.HTML
            }.let { api.execute(it) }
        }
}



