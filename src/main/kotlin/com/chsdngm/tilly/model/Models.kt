package com.chsdngm.tilly.model

import com.chsdngm.tilly.utility.mention
import org.telegram.telegrambots.meta.api.objects.Update
import java.time.Instant

const val week: Long = 60 * 60 * 24 * 7

data class MemeEntity(
    val chatId: Long,
    val messageId: Int,
    val senderId: Int,
    val fileId: String,
    val privateMessageId: Int? = null,
    val channelId: Long? = null,
    val channelMessageId: Int? = null) {

  fun isPublishedOnChannel(): Boolean = channelId != null && channelMessageId != null
}

data class VoteEntity(
    val chatId: Long,
    val messageId: Int,
    val voterId: Int,
    val voteValue: VoteValue
)

enum class VoteValue(val emoji: String) {
  UP("\uD83D\uDC8E"),
  DOWN("\uD83D\uDCA9")
}

class MemeStatsEntry(vararg val counts: Pair<VoteValue, Int>)

class VoteUpdate(update: Update) {
  val fromId: Int = update.callbackQuery.from.id
  val messageId: Int = update.callbackQuery.message.messageId
  val isFrom: SourceType = when {
    update.callbackQuery.message.isChannelMessage -> SourceType.CHANNEL
    update.callbackQuery.message.isSuperGroupMessage -> SourceType.CHAT
    else -> throw Exception("Unknown vote source type")
  }
  val isNotProcessable: Boolean = Instant.ofEpochSecond(update.callbackQuery.message.date.toLong()) < Instant.now().minusSeconds(week)
  val voteValue: VoteValue = VoteValue.valueOf(update.callbackQuery.data)
  val caption: String? = update.callbackQuery.message.caption

  enum class SourceType {
    CHAT,
    CHANNEL
  }

  override fun toString(): String {
    return "VoteUpdate(fromId=$fromId, messageId=$messageId, isFrom=$isFrom, isMessageOld=$isNotProcessable, voteValue=$voteValue, caption=$caption)"
  }
}

class MemeUpdate(update: Update) {
  val messageId: Int = update.message.messageId
  val caption: String? = update.message.caption
  val fileId: String = update.message.photo[0].fileId
  val senderId: Int = update.message.from.id
  val senderName: String = update.message.from.mention()

  override fun toString(): String {
    return "MemeUpdate(messageId=$messageId, caption=$caption, fileId='$fileId', senderId=$senderId, senderName='$senderName')"
  }
}

class CommandUpdate(update: Update) {
  val senderId: Long = update.message.chatId
  val value: Command? = Command.from(update.message.text)

  enum class Command(val value: String) {
    STATS("/stats"),
    HELP("/help"),
    START("/start")
    ;

    companion object {
      private val map = values().associateBy(Command::value)
      fun from(value: String) = map[value]
    }
  }
}
