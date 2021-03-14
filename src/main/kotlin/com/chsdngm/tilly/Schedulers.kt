package com.chsdngm.tilly

import com.chsdngm.tilly.model.Meme
import com.chsdngm.tilly.publish.MemePublisher
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.utility.TillyConfig
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHANNEL_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHAT_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.api
import com.chsdngm.tilly.utility.mention
import com.chsdngm.tilly.utility.setChatId
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.util.ResourceUtils
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto
import java.io.File
import javax.imageio.ImageIO

@Service
@EnableScheduling
final class Schedulers(
    private val memeRepository: MemeRepository,
    private val memePublisher: MemePublisher,
) {

  private val log = LoggerFactory.getLogger(javaClass)

  private val ebuchkaImage = ResourceUtils.getFile("classpath:images/ebuchka.jpg")

  // every hour since 8 am till 1 am Moscow time
  @Scheduled(cron = "0 0 5-22/1 * * *")
  private fun publishMeme() =
      runCatching {
        if (TillyConfig.publishEnabled) {
          memePublisher.publishMemeIfSomethingExists()
        } else {
          log.info("meme publishing is disabled")
        }
      }.onFailure {
        SendMessage()
            .setChatId(TillyConfig.BETA_CHAT_ID)
            .setText(it.format(update = null))
            .setParseMode(ParseMode.HTML)
            .apply { api.execute(this) }
      }

  @Scheduled(cron = "0 0 19 * * WED")
  private fun sendMemeOfTheWeek() =
      runCatching {
        memeRepository.findMemeOfTheWeek()?.let { meme ->
          val winner = api.execute(
              GetChatMember()
                  .setChatId(CHANNEL_ID)
                  .setUserId(meme.senderId))
              .user.mention()

          SendMessage()
              .setChatId(CHANNEL_ID)
              .setParseMode(ParseMode.HTML)
              .setReplyToMessageId(meme.channelMessageId)
              .setText("Поздравляем $winner с мемом недели!").let {
                api.execute(it)
              }.also {
                api.execute(PinChatMessage(it.chatId, it.messageId))
              }

          memeRepository.saveMemeOfWeek(meme.id)
        } ?: log.info("can't find meme of the week")
      }
          .onSuccess { log.info("successful send meme of the week") }
          .onFailure { log.error("can't send meme of the week because of", it) }

  @Scheduled(cron = "0 0 17 * * *")
  private fun replyOnForgottenMemes() = memeRepository.findForgottenMemes().also { memes ->
    log.info("Found ${memes.size} forgotten memes. Replying...")

    memes.forEach { meme ->
      SendPhoto()
          .setChatId(CHAT_ID)
          .setPhoto(ebuchkaImage)
          .setReplyToMessageId(meme.moderationChatMessageId)
          .let { api.execute(it) }
    }
  }
}



