package com.chsdngm.tilly

import com.chsdngm.tilly.model.Meme
import com.chsdngm.tilly.publish.MemePublisher
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.utility.TillyConfig
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHANNEL_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHAT_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.api
import com.chsdngm.tilly.utility.mention
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto

@Service
@EnableScheduling
final class Schedulers(private val memeRepository: MemeRepository,
                       private val memePublisher: MemePublisher) {

  private val log = LoggerFactory.getLogger(javaClass)

// every 2 hours since 8 till 24 Moscow time
  @Scheduled(cron = "0 0 5-22/1 * * *")
  private fun publishMeme() =
  runCatching {
    memePublisher.publishMemeIfSomethingExists()
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

  @Scheduled(cron = "0 0 20 31 12 *")
  private fun sendMemesOfTheYear() =
      runCatching {
        fun formatMemeOfTheYearCaption(meme: Meme): String {
          val userMention = api.execute(GetChatMember()
              .setChatId(CHAT_ID)
              .setUserId(meme.senderId))
              .user.mention()
          val votes = meme.votes
              .groupingBy { vote -> vote.value }
              .eachCount()
              .map { entry -> entry.key.emoji + " " + entry.value }
              .joinToString(prefix = "(", postfix = ")", separator = ", ")
          return "$userMention $votes"
        }

        SendMessage(CHANNEL_ID, "Топ мемсы прошедшего года:").let { api.execute(it) }
        //TODO add date filter there
        SendMediaGroup(CHANNEL_ID, memeRepository.findAll().map {
          InputMediaPhoto(it.fileId, formatMemeOfTheYearCaption(it))
              .setParseMode(ParseMode.HTML)
        }).let { api.execute(it) }
      }
          .onSuccess { log.info("successfully sent memes of the year") }
          .onFailure { log.error("can't send memes of the year because of", it) }
}



