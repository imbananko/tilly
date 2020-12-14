package com.chsdngm.tilly.publish

import com.chsdngm.tilly.model.Meme
import com.chsdngm.tilly.model.MemeStatus
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.repository.PublishMemeRepository
import com.chsdngm.tilly.utility.TillyConfig
import com.chsdngm.tilly.utility.createMarkup
import com.chsdngm.tilly.utility.updateStatsInSenderChat
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import javax.transaction.Transactional

@Service
class MemePublisher(private val publishMemeRepository: PublishMemeRepository,
                    private val memeRepository: MemeRepository) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional
  fun publishMemeIfSomethingExists() {
    val memeToPublish = publishMemeRepository.findMemeToPublish()

    if (memeToPublish != null) {
      memeRepository.findById(memeToPublish.memeId)
        .filter { it.channelMessageId == null }
        .ifPresent { meme ->
          meme.channelMessageId = sendMemeToChannel(meme).messageId
          updateStatsInSenderChat(meme, MemeStatus.PUBLISHED)
        }

      publishMemeRepository.delete(memeToPublish)
    } else {
      log.info("there is nothing to post")
    }

  }

  private fun sendMemeToChannel(meme: Meme) =
    SendPhoto()
      .setChatId(TillyConfig.CHANNEL_ID)
      .setPhoto(meme.fileId)
      .setReplyMarkup(createMarkup(meme.votes.groupingBy { it.value }.eachCount()))
      .setCaption(meme.caption)
      .let { TillyConfig.api.execute(it) }
      .also { log.info("sent meme to channel. meme=$meme") }
}