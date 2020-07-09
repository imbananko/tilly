package com.chsdngm.tilly.model

import com.chsdngm.tilly.utility.mention
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import java.io.File
import java.time.Instant


private val trashCaptionParts = listOf("sender", "photo from")
const val week: Long = 60 * 60 * 24 * 7

class VoteUpdate(update: Update) {
  val fromId: Int = update.callbackQuery.from.id
  val messageId: Int = update.callbackQuery.message.messageId
  val isFrom: VoteSourceType = when {
    update.callbackQuery.message.isChannelMessage -> VoteSourceType.CHANNEL
    update.callbackQuery.message.isSuperGroupMessage -> VoteSourceType.CHAT
    else -> throw Exception("Unknown vote source type")
  }
  val isOld: Boolean = Instant.ofEpochSecond(update.callbackQuery.message.date.toLong()) < Instant.now().minusSeconds(week)
  val voteValue: VoteValue = VoteValue.valueOf(update.callbackQuery.data)
  val callbackQueryId: String = update.callbackQuery.id

  override fun toString(): String {
    return "VoteUpdate(fromId=$fromId, messageId=$messageId, isFrom=$isFrom, voteValue=$voteValue)"
  }
}

class MemeUpdate(update: Update) {
  val messageId: Int = update.message.messageId
  val caption: String? = update.message.caption?.takeIf { caption ->
    val lowerCaseCaption = caption.toLowerCase()
    !trashCaptionParts.any { lowerCaseCaption.contains(it) }
  }
  val fileId: String = update.message.photo.maxBy { it.fileSize }!!.fileId
  val user: User = update.message.from
  val senderName: String = update.message.from.mention()

  lateinit var file: File

  override fun toString(): String {
    return "MemeUpdate(chatMessageId=$messageId, caption=$caption, fileId='$fileId', user='${user.id})"
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

  override fun toString(): String {
    return "CommandUpdate(senderId=$senderId, value=$value)"
  }
}

class PrivateVoteUpdate(update: Update) {
  val senderId: Long = update.callbackQuery.message.chatId
  val messageId: Int = update.callbackQuery.message.messageId
  val voteValue: PrivateVoteValue = PrivateVoteValue.valueOf(update.callbackQuery.data)
}