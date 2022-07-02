package com.chsdngm.tilly.publish

import com.chsdngm.tilly.exposed.Meme
import com.chsdngm.tilly.exposed.MemeDao
import com.chsdngm.tilly.model.MemeStatus
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHANNEL_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.api
import com.chsdngm.tilly.utility.createMarkup
import com.chsdngm.tilly.utility.updateStatsInSenderChat
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile

@Service
class MemePublisher(private val memeDao: MemeDao) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publishMemeIfSomethingExists() {
        val memeToPublish = memeDao.findFirstByStatusOrderByCreated(MemeStatus.SCHEDULED)

        if (memeToPublish != null) {
            memeToPublish.channelMessageId = sendMemeToChannel(memeToPublish).messageId
            memeToPublish.status = MemeStatus.PUBLISHED
            memeDao.update(memeToPublish)
            updateStatsInSenderChat(memeToPublish)
        } else {
            log.info("there is nothing to post")
        }
    }

    private fun sendMemeToChannel(meme: Meme) = SendPhoto().apply {
        chatId = CHANNEL_ID
        photo = InputFile(meme.fileId)
        replyMarkup = createMarkup(meme.votes.groupingBy { it.value }.eachCount())
        parseMode = ParseMode.HTML
        caption = meme.caption
    }.let(api::execute).also { log.info("sent meme to channel. meme=$meme") }
}