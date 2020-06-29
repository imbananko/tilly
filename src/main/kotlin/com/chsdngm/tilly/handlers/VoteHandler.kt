package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.*
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.utility.TelegramConfig.Companion.CHANNEL_ID
import com.chsdngm.tilly.utility.TelegramConfig.Companion.CHAT_ID
import com.chsdngm.tilly.utility.TelegramConfig.Companion.api
import com.chsdngm.tilly.utility.createMarkup
import com.chsdngm.tilly.utility.hasLocalTag
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText

@Service
class VoteHandler(private val memeRepository: MemeRepository) : AbstractHandler<VoteUpdate> {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun handle(update: VoteUpdate) {
    val meme = when (update.isFrom) {
      VoteSourceType.CHANNEL -> memeRepository.findByChannelMessageId(update.messageId)
      VoteSourceType.CHAT -> memeRepository.findByChatMessageId(update.messageId)
    } ?: return

    val vote = Vote(VoteKey(meme.chatMessageId, update.fromId), update.voteValue, update.isFrom)

    if (update.isNotProcessable || meme.senderId == vote.key.voterId) return

    meme.votes.firstOrNull { it.key.voterId == vote.key.voterId }.also { found ->
      if (found == null)
        meme.votes.add(vote)
      else {
        meme.votes.remove(found)
        if (found == vote) {
          sendPopupNotification(update.callbackQueryId, "Вы удалили свой голос с этого мема")
        } else {
          meme.votes.add(found.copy(value = vote.value).apply {
            when (this.value) {
              VoteValue.UP -> "Вы обогатили этот мем ${VoteValue.UP.emoji}"
              VoteValue.DOWN -> "Вы засрали этот мем ${VoteValue.DOWN.emoji}"
            }.let { sendPopupNotification(update.callbackQueryId, it) }
          })
        }
      }
    }

    updateChatMarkup(meme)

    memeRepository.save(
        if (meme.channelMessageId == null && readyForShipment(meme))
          meme.copy(channelMessageId = sendMemeToChannel(meme).messageId)
        else
          meme)

    runCatching {
      with(StringBuilder()) {
        this.append(
            if (hasLocalTag(meme.caption))
              "так как мем локальный, на канал он отправлен не будет"
            else if (meme.channelMessageId != null || readyForShipment(meme))
              "мем отправлен на канал. "
            else
              "мем на модерации. ")

        this.append("статистика: \n\n")
        this.append(meme.votes.groupingBy { it.value }.eachCount().entries.sortedBy { it.key }.joinToString(
            transform = { (value, sum) -> "${value.emoji}: $sum" }))

        updateStatsInSenderChat(meme, this.toString())
      }
    }.onFailure { log.error("Failed to update private caption", it) }

    log.info("Processed vote update=$update")
  }

  fun sendPopupNotification(callbackQueryId: String, text: String) {
    AnswerCallbackQuery()
        .setCallbackQueryId(callbackQueryId)
        .setText(text).let { api.execute(it) }
  }

  private fun updateChatMarkup(meme: Meme) =
      EditMessageReplyMarkup()
          .setChatId(CHAT_ID)
          .setMessageId(meme.chatMessageId)
          .setReplyMarkup(createMarkup(meme.votes.groupingBy { it.value }.eachCount()))
          .let { api.execute(it) }

  private fun updateChannelMarkup(meme: Meme) =
      EditMessageReplyMarkup()
          .setChatId(CHANNEL_ID)
          .setMessageId(meme.channelMessageId)
          .setReplyMarkup(createMarkup(meme.votes.groupingBy { it.value }.eachCount()))
          .let { api.execute(it) }

  private fun sendMemeToChannel(meme: Meme) =
      SendPhoto()
          .setChatId(CHANNEL_ID)
          .setPhoto(meme.fileId)
          .setReplyMarkup(createMarkup(meme.votes.groupingBy { it.value }.eachCount()))
          .setCaption(meme.caption)
          .let { api.execute(it) }
          .also { log.info("Sent meme to channel=$meme") }

  private fun readyForShipment(meme: Meme): Boolean = with(meme.votes.map { it.value }) {
    this.filter { it == VoteValue.UP }.size - this.filter { it == VoteValue.DOWN }.size >= 1 && !hasLocalTag(meme.caption)
  }

  private fun updateStatsInSenderChat(meme: Meme, stats: String) =
      EditMessageText()
          .setChatId(meme.senderId.toString())
          .setMessageId(meme.privateMessageId)
          .setText(stats).let { api.execute(it) }
}
