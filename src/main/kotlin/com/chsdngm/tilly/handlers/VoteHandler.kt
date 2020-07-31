package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.Meme
import com.chsdngm.tilly.model.Vote
import com.chsdngm.tilly.model.VoteSourceType.CHANNEL
import com.chsdngm.tilly.model.VoteSourceType.CHAT
import com.chsdngm.tilly.model.VoteUpdate
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHANNEL_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHAT_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.MODERATION_THRESHOLD
import com.chsdngm.tilly.utility.TillyConfig.Companion.api
import com.chsdngm.tilly.utility.createMarkup
import com.chsdngm.tilly.utility.hasLocalTag
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import javax.transaction.Transactional

@Service
class VoteHandler(private val memeRepository: MemeRepository) : AbstractHandler<VoteUpdate> {

  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional
  override fun handle(update: VoteUpdate) {
    if (update.isOld) {
      sendPopupNotification(update.callbackQueryId, "Мем слишком стар")
      return
    }

    val meme = when (update.isFrom) {
      CHANNEL -> memeRepository.findMemeByChannelMessageId(update.messageId)
      CHAT -> memeRepository.findMemeByModerationChatIdAndModerationChatMessageId(CHAT_ID, update.messageId)
      else -> return
    } ?: return

    //TODO: refactor this
    val vote = Vote(meme.id, update.voterId, if (update.isFrom == CHANNEL) CHANNEL_ID else CHAT_ID, update.voteValue)

    if (meme.senderId == vote.voterId) {
      sendPopupNotification(update.callbackQueryId, "Голосуй за других, а не за себя")
      return
    }

    meme.votes.firstOrNull { it.voterId == vote.voterId }?.let { found ->
      if (found.value == vote.value) {
        sendPopupNotification(update.callbackQueryId, "Вы удалили свой голос с этого мема")
        meme.votes.remove(found)
      } else {
        found.value = vote.value

        when (vote.value) {
          VoteValue.UP -> "Вы обогатили этот мем ${VoteValue.UP.emoji}"
          VoteValue.DOWN -> "Вы засрали этот мем ${VoteValue.DOWN.emoji}"
        }.let { sendPopupNotification(update.callbackQueryId, it) }
      }
    } ?: meme.votes.add(vote)

    updateMarkup(meme)

    if (meme.channelMessageId == null && readyForShipment(meme))
      meme.channelMessageId = sendMemeToChannel(meme).messageId

    with(StringBuilder()) {
      this.append(
          if (hasLocalTag(meme.caption))
            "так как мем локальный, на канал он отправлен не будет. "
          else if (meme.channelMessageId != null || readyForShipment(meme))
            "мем отправлен на канал. "
          else
            "мем на модерации. "
      )

      this.append(meme.votes.groupingBy { it.value }.eachCount().entries.sortedBy { it.key }.joinToString(
          prefix = "статистика: \n\n", transform = { (value, sum) -> "${value.emoji}: $sum" }))

      updateStatsInSenderChat(meme, this.toString())
      log.info("processed vote update=$update")
    }
  }

  fun sendPopupNotification(callbackQueryId: String, text: String): Boolean = AnswerCallbackQuery()
      .setCallbackQueryId(callbackQueryId)
      .setText(text).let { api.execute(it) }

  private fun updateMarkup(meme: Meme) {
    meme.channelMessageId?.let {
      EditMessageReplyMarkup()
          .setChatId(CHANNEL_ID)
          .setMessageId(meme.channelMessageId)
          .setReplyMarkup(createMarkup(meme.votes.groupingBy { it.value }.eachCount()))
          .let { api.execute(it) }
    }

    if (meme.moderationChatId == CHAT_ID) {
      EditMessageReplyMarkup()
          .setChatId(CHAT_ID)
          .setMessageId(meme.moderationChatMessageId)
          .setReplyMarkup(createMarkup(meme.votes.groupingBy { it.value }.eachCount()))
          .let { api.execute(it) }
    }
  }

  private fun sendMemeToChannel(meme: Meme) =
      SendPhoto()
          .setChatId(CHANNEL_ID)
          .setPhoto(meme.fileId)
          .setReplyMarkup(createMarkup(meme.votes.groupingBy { it.value }.eachCount()))
          .setCaption(meme.caption)
          .let { api.execute(it) }
          .also { log.info("sent meme to channel. meme=$meme") }

  private fun readyForShipment(meme: Meme): Boolean = with(meme.votes.map { it.value }) {
    this.filter { it == VoteValue.UP }.size - this.filter { it == VoteValue.DOWN }.size >= MODERATION_THRESHOLD && !hasLocalTag(meme.caption)
  }

  private fun updateStatsInSenderChat(meme: Meme, stats: String) =
      EditMessageText()
          .setChatId(meme.senderId.toString())
          .setMessageId(meme.privateReplyMessageId)
          .setText(stats).let { api.execute(it) }
}
