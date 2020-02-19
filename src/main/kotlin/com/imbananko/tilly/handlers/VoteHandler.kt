package com.imbananko.tilly.handlers

import com.imbananko.tilly.model.MemeEntity
import com.imbananko.tilly.model.VoteEntity
import com.imbananko.tilly.model.VoteUpdate
import com.imbananko.tilly.model.VoteValue
import com.imbananko.tilly.repository.MemeRepository
import com.imbananko.tilly.repository.VoteRepository
import com.imbananko.tilly.utility.BotConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup

@Component
class VoteHandler(private val memeRepository: MemeRepository,
                  private val voteRepository: VoteRepository,
                  private val botConfig: BotConfig) : AbstractHandler<VoteUpdate>(), BotConfig by botConfig {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun handle(update: VoteUpdate) {
    val meme = memeRepository.findMemeByChat(chatId, update.messageId) ?: return
    val vote = VoteEntity(chatId, update.messageId, update.fromId, update.voteValue)

    if (update.isMessageOld || meme.senderId == vote.voterId) return

    val votes = voteRepository.getVotes(meme)
        .associate { Pair(it.voterId, it.voteValue) }
        .toMutableMap().also {
          it.merge(vote.voterId, vote.voteValue) { old, new -> if (old == new) null else new }
        }

    val markup = createMarkup(votes.values.groupingBy { it }.eachCount())
    val privateCaptionPrefix =
        if (meme.isPublishedOnChannel() || readyForShipment(votes)) "мем отправлен на канал"
        else "мем на модерации"

    updateChatMarkup(update.messageId, markup)
    meme.channelMessageId?.let { updateChannelMarkup(it, markup) } ?: if (readyForShipment(votes)) {
      val captionForChannel = update.caption?.split("Sender: ")?.firstOrNull() ?: ""
      sendMemeToChannel(meme, captionForChannel, markup).also {
        memeRepository.update(meme, meme.copy(channelId = it.chatId, channelMessageId = it.messageId))
      }
    }

    val caption = votes.values.groupingBy { it }.eachCount().entries.joinToString(
        prefix = "${update.caption ?: ""}\n\n$privateCaptionPrefix. статистика: \n\n",
        transform = { (value, sum) -> "${value.emoji}: $sum" })

    if (meme.privateMessageId != null) updateCaptionInSenderChat(meme, caption)

    log.info("Processed channel vote=$vote")
  }

  private fun updateChatMarkup(messageId: Int, markup: InlineKeyboardMarkup) =
      execute(EditMessageReplyMarkup()
          .setChatId(chatId)
          .setMessageId(messageId)
          .setReplyMarkup(markup)).also { log.debug("Updated chat markup") }

  private fun updateChannelMarkup(messageId: Int, markup: InlineKeyboardMarkup) =
      execute(EditMessageReplyMarkup()
          .setChatId(chatId)
          .setMessageId(messageId)
          .setReplyMarkup(markup)).also { log.debug("Updated channel markup") }

  private fun sendMemeToChannel(meme: MemeEntity, caption: String, markup: InlineKeyboardMarkup) =
      execute(SendPhoto()
          .setChatId(channelId)
          .setPhoto(meme.fileId)
          .setCaption(caption)
          .setReplyMarkup(markup)).also { log.info("Sent meme to channel=$meme") }

  private fun readyForShipment(votes: MutableMap<Int, VoteValue>): Boolean =
      votes.values.filter { it == VoteValue.UP }.size - votes.values.filter { it == VoteValue.DOWN }.size >= 5

  private fun updateCaptionInSenderChat(meme: MemeEntity, caption: String) =
      runCatching {
        execute(EditMessageCaption()
            .setChatId(meme.senderId.toString())
            .setMessageId(meme.privateMessageId)
            .setCaption(caption)
        )
      }.onFailure {
        log.error("Failed update caption in private chat=$chatId", it)
      }




}
