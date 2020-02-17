package com.imbananko.tilly.model

import org.telegram.telegrambots.meta.api.objects.Update
import java.time.Instant

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
  val fromId: Int = update.callbackQuery.message.from.id
  val messageId: Int = update.callbackQuery.message.messageId
  val isMessageOld: Boolean = Instant.ofEpochSecond(update.callbackQuery.message.date.toLong()) < Instant.now().minusSeconds(60 * 60 * 24 * 7)
  val voteValue: VoteValue = VoteValue.valueOf(update.callbackQuery.data.split(" ".toRegex()).dropLastWhile { it.isEmpty() }[0])
  val caption: String? = update.callbackQuery.message.caption
}

class MemeUpdate(update: Update) {
  val messageId: Int = update.message.messageId
  val caption: String? = update.message.caption
  val fileId: String = update.message.photo[0].fileId
  val senderId: Int = update.message.from.id
  val senderName: String = update.message.from.userName?.let { "@$it" } ?: update.message.from.firstName ?: update.message.from.lastName
}

class CommandUpdate(update: Update) {
  val senderId: Long = update.message.chatId
  val value: Command? = Command.from(update.message.text)
}

enum class Command(val value: String) {
  STATS("/stats");

  companion object {
    private val map = values().associateBy(Command::value)
    fun from(value: String) = map[value]
  }
}