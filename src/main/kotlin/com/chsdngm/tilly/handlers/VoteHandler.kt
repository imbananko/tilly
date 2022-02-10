package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.*
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHANNEL_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHAT_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.MODERATION_THRESHOLD
import com.chsdngm.tilly.utility.TillyConfig.Companion.api
import com.chsdngm.tilly.utility.createMarkup
import com.chsdngm.tilly.utility.updateStatsInSenderChat
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
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
      CHANNEL_ID -> memeRepository.findMemeByChannelMessageId(update.messageId)
      CHAT_ID -> memeRepository.findMemeByModerationChatIdAndModerationChatMessageId(CHAT_ID.toLong(), update.messageId)
      else -> return
    } ?: return

    val vote = Vote(meme.id, update.voterId.toInt(), update.isFrom.toLong(), update.voteValue)

    if (meme.senderId == vote.voterId) {
      sendPopupNotification(update.callbackQueryId, "Голосуй за других, а не за себя")
      return
    }

    meme.votes.firstOrNull { it.voterId == vote.voterId }?.let { found ->
      if (meme.votes.removeIf { it.voterId == vote.voterId && it.value == vote.value }) {
        sendPopupNotification(update.callbackQueryId, "Вы удалили свой голос с этого мема")
      } else {
        found.value = vote.value
        found.sourceChatId = vote.sourceChatId

        when (vote.value) {
          VoteValue.UP -> "Вы обогатили этот мем ${VoteValue.UP.emoji}"
          VoteValue.DOWN -> "Вы засрали этот мем ${VoteValue.DOWN.emoji}"
        }.let { sendPopupNotification(update.callbackQueryId, it) }
      }
    } ?: meme.votes.add(vote).also {
      when (vote.value) {
        VoteValue.UP -> "Вы обогатили этот мем ${VoteValue.UP.emoji}"
        VoteValue.DOWN -> "Вы засрали этот мем ${VoteValue.DOWN.emoji}"
      }.let { sendPopupNotification(update.callbackQueryId, it) }
    }

    updateMarkup(meme)

    if (meme.status.canBeScheduled() && readyForShipment(meme))
      meme.status = MemeStatus.SCHEDULED

    updateStatsInSenderChat(meme)
    log.info("processed vote update=$update")
  }

  fun sendPopupNotification(userCallbackQueryId: String, popupText: String): Boolean =
    AnswerCallbackQuery().apply {
      cacheTime = 0
      callbackQueryId = userCallbackQueryId
      text = popupText
    }.let { api.execute(it) }

  private fun updateMarkup(meme: Meme) {
    meme.channelMessageId?.let {
      EditMessageReplyMarkup().apply {
        chatId = CHANNEL_ID
        messageId = meme.channelMessageId
        replyMarkup = createMarkup(meme.votes.groupingBy { it.value }.eachCount())
      }.let { api.execute(it) }
    }

    if (meme.moderationChatId.toString() == CHAT_ID) {
      EditMessageReplyMarkup().apply {
        chatId = CHAT_ID
        messageId = meme.moderationChatMessageId
        replyMarkup = createMarkup(meme.votes.groupingBy { it.value }.eachCount())
      }.let { api.execute(it) }
    }
  }


  private fun readyForShipment(meme: Meme): Boolean =
    with(meme.votes.map { it.value }) {
      this.filter { it == VoteValue.UP }.size - this.filter { it == VoteValue.DOWN }.size >= MODERATION_THRESHOLD
    }

}
