package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.MemeEntity
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.repository.VoteRepository
import com.chsdngm.tilly.utility.BotConfig
import com.chsdngm.tilly.utility.BotConfigImpl
import com.chsdngm.tilly.utility.mention
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.meta.ApiContext
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto

@Component
@EnableScheduling
final class Schedulers(private val memeRepository: MemeRepository,
                       private val voteRepository: VoteRepository,
                       private val botConfig: BotConfigImpl) : DefaultAbsSender(ApiContext.getInstance(DefaultBotOptions::class.java)), BotConfig by botConfig {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(cron = "0 0 20 30 * WED")
  private fun sendMemeOfTheWeek() {
    runCatching {
      val meme: MemeEntity? = memeRepository.findMemeOfTheWeek()

      meme ?: log.info("Can't find meme of the week")
      meme ?: return

      memeRepository.markAsMemeOfTheWeek(meme)

      val winner = execute(
          GetChatMember()
              .setChatId(channelId)
              .setUserId(meme.senderId))
          .user
      val congratulationText = "Поздравляем ${winner.mention()} с мемом недели!"
      val memeOfTheWeekMessage = runCatching {
        execute(
            SendMessage()
                .setChatId(channelId)
                .setParseMode(ParseMode.HTML)
                .setReplyToMessageId(meme.channelMessageId)
                .setText(congratulationText)
        )
      }.getOrElse { error ->
        log.error("Can't reply to meme of the week because of", error)
        log.info("Send meme of the week as new message")

        val statistics = voteRepository.getVotes(meme)
            .groupingBy { vote -> vote.voteValue }
            .eachCount()
        val fallbackMemeOfTheWeekMessage = execute(
            SendPhoto()
                .setChatId(channelId)
                .setPhoto(meme.fileId)
                .setParseMode(ParseMode.HTML)
                .setCaption(congratulationText)
                .setReplyMarkup(AbstractHandler.createMarkup(statistics))
        )

        memeRepository.update(meme, meme.copy(channelMessageId = fallbackMemeOfTheWeekMessage.messageId))

        fallbackMemeOfTheWeekMessage
      }
      execute(PinChatMessage(memeOfTheWeekMessage.chatId, memeOfTheWeekMessage.messageId))
    }
        .onSuccess { log.info("Successful send meme of the week") }
        .onFailure { throwable -> log.error("Can't send meme of the week because of", throwable) }
  }

  @Scheduled(cron = "0 0 20 31 12 *")
  private fun sendMemesOfTheYear() {
    fun formatMemeTheYearCaption(meme: MemeEntity): String {
      val userMention = execute(GetChatMember()
          .setChatId(chatId)
          .setUserId(meme.senderId))
          .user.mention()
      val votes = voteRepository.getVotes(meme)
          .groupingBy { vote -> vote.voteValue }
          .eachCount()
          .map { entry -> entry.key.emoji + " " + entry.value }
          .joinToString(prefix = "(", postfix = ")", separator = ", ")
      return "$userMention $votes"
    }

    runCatching {
      execute(SendMessage(channelId, "Топ мемсы прошедшего года:"))
      execute(
          SendMediaGroup(channelId, memeRepository.findMemesOfTheYear().map { meme ->
            InputMediaPhoto(meme.fileId, formatMemeTheYearCaption(meme))
                .setParseMode(ParseMode.HTML)
          })
      )
    }
        .onSuccess { log.info("Successfully sent memes of the year") }
        .onFailure { throwable -> log.error("Can't send memes of the year because of", throwable) }
  }
}
