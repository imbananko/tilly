package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.Meme
import com.chsdngm.tilly.model.MemeStatus
import com.chsdngm.tilly.model.PublishMemeTask
import com.chsdngm.tilly.model.Vote
import com.chsdngm.tilly.model.VoteUpdate
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.repository.PublishMemeRepository
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHANNEL_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHAT_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.MODERATION_THRESHOLD
import com.chsdngm.tilly.utility.TillyConfig.Companion.api
import com.chsdngm.tilly.utility.createMarkup
import com.chsdngm.tilly.utility.hasLocalTag
import com.chsdngm.tilly.utility.updateStatsInSenderChat
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import javax.transaction.Transactional

@Service
class VoteHandler(private val memeRepository: MemeRepository,
                  private val publishMemeRepository: PublishMemeRepository) : AbstractHandler<VoteUpdate> {

  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional
  override fun handle(update: VoteUpdate) {
    if (update.isOld) {
      sendPopupNotification(update.callbackQueryId, "Мем слишком стар")
      return
    }

    val meme = when (update.isFrom) {
      CHANNEL_ID -> memeRepository.findMemeByChannelMessageId(update.messageId)
      CHAT_ID -> memeRepository.findMemeByModerationChatIdAndModerationChatMessageId(CHAT_ID, update.messageId)
      else -> return
    } ?: return

    val vote = Vote(meme.id, update.voterId, update.isFrom, update.voteValue)

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

    val readyForShipment = readyForShipment(meme)
    val isInPublishQueue = meme.channelMessageId == null && publishMemeRepository.existsById(meme.id)

    if (meme.channelMessageId == null && readyForShipment && !isInPublishQueue)
      publishMemeRepository.save(PublishMemeTask(meme.id))

    val status =
      when {
        hasLocalTag(meme.caption) -> MemeStatus.LOCAL
        meme.channelMessageId != null -> MemeStatus.PUBLISHED
        readyForShipment || isInPublishQueue -> MemeStatus.SCHEDULED
        else -> MemeStatus.MODERATION
      }

    updateStatsInSenderChat(meme, status)
    log.info("processed vote update=$update")
  }

  fun sendPopupNotification(callbackQueryId: String, text: String): Boolean = AnswerCallbackQuery()
      .setCacheTime(0)
      .setCallbackQueryId(callbackQueryId)
      .setText(text)
      .let { api.execute(it) }

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

  private fun readyForShipment(meme: Meme): Boolean = with(meme.votes.map { it.value }) {
    this.filter { it == VoteValue.UP }.size - this.filter { it == VoteValue.DOWN }.size >= MODERATION_THRESHOLD && !hasLocalTag(meme.caption)
  }

}
