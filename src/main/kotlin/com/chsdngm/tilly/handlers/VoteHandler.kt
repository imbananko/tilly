package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.*
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.repository.VoteRepository
import com.chsdngm.tilly.utility.BotConfig
import com.chsdngm.tilly.utility.BotConfigImpl
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText

@Component
class VoteHandler(private val memeRepository: MemeRepository,
                  private val voteRepository: VoteRepository,
                  private val botConfig: BotConfigImpl) : AbstractHandler<VoteUpdate>(), BotConfig by botConfig {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun handle(update: VoteUpdate) {
    val meme = when (update.isFrom) {
      SourceType.CHANNEL -> memeRepository.findByChannelMessageId(update.messageId)
      SourceType.CHAT -> memeRepository.findByChatMessageId(update.messageId)
    } ?: return

    val vote = VoteEntity(meme.chatMessageId, update.fromId, update.voteValue, update.isFrom)

    if (update.isNotProcessable || meme.senderId == vote.voterId) return

    val votes = voteRepository.getVotes(meme)
        .associate { Pair(it.voterId, it.voteValue) }
        .toMutableMap().also {
          it.merge(vote.voterId, vote.voteValue) { old, new -> if (old == new) null else new }
        }

    val groupedVotes = votes.values.groupingBy { it }.eachCount()

    val privateCaptionPrefix =
        if (meme.isPublishedOnChannel() || readyForShipment(votes)) "мем отправлен на канал"
        else "мем на модерации"

    updateChatMarkup(meme.chatMessageId, groupedVotes)

    meme.channelMessageId?.also { updateChannelMarkup(it, groupedVotes) } ?: if (readyForShipment(votes)) {
      val captionForChannel = update.caption?.split("Sender: ")?.firstOrNull() ?: ""
      val channelMessageId = sendMemeToChannel(meme, captionForChannel, groupedVotes).messageId
      memeRepository.update(meme, meme.copy(channelMessageId = channelMessageId))
    }

    if (votes.containsKey(vote.voterId)) voteRepository.insertOrUpdate(vote) else voteRepository.delete(vote)

    val caption = groupedVotes.entries.sortedBy { it.key }.joinToString(
        prefix = "${update.caption ?: ""}\n\n$privateCaptionPrefix. статистика: \n\n",
        transform = { (value, sum) -> "${value.emoji}: $sum" })

    updateStatsInSenderChat(meme, caption)
    log.info("Processed vote update=$update")
  }

  private fun updateChatMarkup(messageId: Int, votes: Map<VoteValue, Int>) =
      EditMessageReplyMarkup()
          .setChatId(chatId)
          .setMessageId(messageId)
          .setReplyMarkup(createMarkup(votes)).let { execute(it) }

  private fun updateChannelMarkup(messageId: Int, votes: Map<VoteValue, Int>) =
      EditMessageReplyMarkup()
          .setChatId(channelId)
          .setMessageId(messageId)
          .setReplyMarkup(createMarkup(votes, messageId)).let { execute(it) }

  private fun sendMemeToChannel(meme: MemeEntity, caption: String, votes: Map<VoteValue, Int>) =
      SendPhoto()
          .setChatId(channelId)
          .setPhoto(meme.fileId)
          .setReplyMarkup(createMarkup(votes, meme.chatMessageId))
          .setCaption(caption)
          .let { execute(it) }

  private fun readyForShipment(votes: MutableMap<Int, VoteValue>): Boolean =
          votes.values.filter { it == VoteValue.UP }.size - votes.values.filter { it == VoteValue.DOWN }.size >= 5

  private fun updateStatsInSenderChat(meme: MemeEntity, stats: String) =
      EditMessageText()
          .setChatId(meme.senderId.toString())
          .setMessageId(meme.privateMessageId)
          .setText(stats).let { execute(it) }
}
