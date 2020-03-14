package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.MemeEntity
import com.chsdngm.tilly.model.VoteEntity
import com.chsdngm.tilly.model.VoteUpdate
import com.chsdngm.tilly.model.VoteUpdate.SourceType.CHANNEL
import com.chsdngm.tilly.model.VoteUpdate.SourceType.CHAT
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.repository.VoteRepository
import com.chsdngm.tilly.utility.BotConfig
import com.chsdngm.tilly.utility.BotConfigImpl
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup

@Component
class VoteHandler(private val memeRepository: MemeRepository,
                  private val voteRepository: VoteRepository,
                  private val botConfig: BotConfigImpl) : AbstractHandler<VoteUpdate>(), BotConfig by botConfig {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun handle(update: VoteUpdate) {
    val meme = when (update.isFrom) {
      CHANNEL -> memeRepository.findByChannelMessageId(update.messageId)
      CHAT -> memeRepository.findByChatMessageId(update.messageId)
    } ?: return

    val vote = VoteEntity(meme.chatMessageId, update.fromId, update.voteValue)

    if (update.isNotProcessable || meme.senderId == vote.voterId) return

    val votes = voteRepository.getVotes(meme)
        .associate { Pair(it.voterId, it.voteValue) }
        .toMutableMap().also {
          it.merge(vote.voterId, vote.voteValue) { old, new -> if (old == new) null else new }
        }

    val markup = createMarkup(votes.values.groupingBy { it }.eachCount())
    val privateCaptionPrefix =
        if (meme.isPublishedOnChannel() || readyForShipment(votes)) "мем отправлен на канал"
        else "мем на модерации"

    updateChatMarkup(meme.chatMessageId, markup)

    meme.channelMessageId?.also { updateChannelMarkup(it, markup) } ?: if (readyForShipment(votes)) {
      val captionForChannel = update.caption?.split("Sender: ")?.firstOrNull() ?: ""
      val channelMessageId = sendMemeToChannel(meme, captionForChannel, markup).messageId
      memeRepository.update(meme, meme.copy(channelMessageId = channelMessageId))
    }

    if (votes.containsKey(vote.voterId)) voteRepository.insertOrUpdate(vote) else voteRepository.delete(vote)

    val caption = votes.values.groupingBy { it }.eachCount().entries.sortedBy { it.key }.joinToString(
        prefix = "${update.caption ?: ""}\n\n$privateCaptionPrefix. статистика: \n\n",
        transform = { (value, sum) -> "${value.emoji}: $sum" })

    updateStatsInSenderChat(meme, caption)
    log.info("Processed vote update=$update")
  }

  private fun updateChatMarkup(messageId: Int, markup: InlineKeyboardMarkup) =
      execute(EditMessageReplyMarkup()
          .setChatId(chatId)
          .setMessageId(messageId)
          .setReplyMarkup(markup)).also { log.debug("Updated chat markup") }

  private fun updateChannelMarkup(messageId: Int, markup: InlineKeyboardMarkup) =
      execute(EditMessageReplyMarkup()
          .setChatId(channelId)
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

  private fun updateStatsInSenderChat(meme: MemeEntity, stats: String) =
        execute(EditMessageText()
            .setChatId(meme.senderId.toString())
            .setMessageId(meme.privateMessageId)
            .setText(stats))
}
