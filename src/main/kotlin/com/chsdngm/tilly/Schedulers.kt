package com.chsdngm.tilly

import com.chsdngm.tilly.publish.MemePublisher
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.utility.TillyConfig
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHANNEL_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHAT_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.api
import com.chsdngm.tilly.utility.mention
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile
import java.io.File

@Service
@EnableScheduling
final class Schedulers(
    private val memeRepository: MemeRepository,
    private val memePublisher: MemePublisher,
) {

  private val log = LoggerFactory.getLogger(javaClass)

  private val ebuchkaImage = File("ebuchka").also {
    ClassPathResource("images/ebuchka.jpg").apply {
      FileUtils.copyInputStreamToFile(this.inputStream, it)
    }
  }

  // every hour since 8 am till 1 am Moscow time
  @Scheduled(cron = "0 0 5-22/1 * * *")
  private fun publishMeme() =
      runCatching {
        if (TillyConfig.publishEnabled) {
          memePublisher.publishMemeIfSomethingExists()
        } else {
          log.info("meme publishing is disabled")
        }
      }.onFailure { fail ->
        SendMessage()
          .also {
            it.chatId = TillyConfig.BETA_CHAT_ID.toString()
            it.text = fail.format(update = null)
            it.parseMode = ParseMode.HTML
          }
          .apply { api.execute(this) }
      }

  @Scheduled(cron = "0 0 19 * * WED")
  private fun sendMemeOfTheWeek() =
      runCatching {
        memeRepository.findMemeOfTheWeek()?.let { meme ->
          val winner = api.execute(
            GetChatMember()
              .also {
                it.chatId = CHANNEL_ID.toString()
                it.userId = meme.senderId.toLong()
              }
          )
              .user.mention()

          SendMessage()
            .also {
              it.setChatId(CHANNEL_ID.toString())
              it.setParseMode(ParseMode.HTML)
              it.setReplyToMessageId(meme.channelMessageId)
              it.setText("Поздравляем $winner с мемом недели!")
            }
            .let {
                api.execute(it)
              }.also {
                api.execute(PinChatMessage(it.chatId.toString(), it.messageId))
              }

          memeRepository.saveMemeOfWeek(meme.id)
        } ?: log.info("can't find meme of the week")
      }
          .onSuccess { log.info("successful send meme of the week") }
          .onFailure { fail ->
            log.error("can't send meme of the week because of", fail)
            SendMessage()
              .also {
                it.setChatId(TillyConfig.BETA_CHAT_ID.toString())
                it.setText(fail.format(update = null))
                it.setParseMode(ParseMode.HTML)
              }
              .apply { api.execute(this) }
          }

  @Scheduled(cron = "0 0 17 * * *")
  private fun replyOnForgottenMemes() = memeRepository.findForgottenMemes().also { memes ->
    log.info("Found ${memes.size} forgotten memes. Replying...")

    memes.forEach { meme ->
      SendPhoto()
        .also {
          it.setChatId(CHAT_ID.toString())
          it.setPhoto(InputFile(ebuchkaImage))
          it.setReplyToMessageId(meme.moderationChatMessageId)
        }
        .let { api.execute(it) }
    }
  }
}



