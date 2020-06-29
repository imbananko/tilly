package com.chsdngm.tilly.model

import com.chsdngm.tilly.utility.mention
import org.hibernate.annotations.Type
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import java.io.File
import java.io.Serializable
import java.time.Instant
import javax.persistence.*

const val week: Long = 60 * 60 * 24 * 7

@Entity
data class Meme(
    @Id val chatMessageId: Int,
    val senderId: Int,
    val fileId: String,
    val caption: String?,
    val privateMessageId: Int? = null,
    val channelMessageId: Int? = null) {

  @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, mappedBy = "key.chatMessageId", orphanRemoval = true)
  var votes: MutableList<Vote> = mutableListOf()
}

@Entity
data class TelegramUser(
    @Id val id: Int,
    val username: String?,
    val firstName: String?,
    val lastName: String?
)

@Entity
data class Vote(
    @EmbeddedId val key: VoteKey,
    @Enumerated(EnumType.STRING) val value: VoteValue,
    @Enumerated(EnumType.STRING) val source: VoteSourceType
)

@Entity
data class Image(
    @Id val fileId: String,
    @Lob @Type(type="org.hibernate.type.BinaryType") val file: ByteArray
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Image

    if (fileId != other.fileId) return false
    if (!file.contentEquals(other.file)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = fileId.hashCode()
    result = 31 * result + file.contentHashCode()
    return result
  }
}

@Embeddable
data class VoteKey(
    val chatMessageId: Int,
    val voterId: Int) : Serializable

enum class VoteValue(val emoji: String) {
  UP("\uD83D\uDC8E"),
  DOWN("\uD83D\uDCA9")
}

enum class VoteSourceType {
  CHAT,
  CHANNEL
}

class VoteUpdate(update: Update) {
  val fromId: Int = update.callbackQuery.from.id
  val messageId: Int = update.callbackQuery.message.messageId
  val isFrom: VoteSourceType = when {
    update.callbackQuery.message.isChannelMessage -> VoteSourceType.CHANNEL
    update.callbackQuery.message.isSuperGroupMessage -> VoteSourceType.CHAT
    else -> throw Exception("Unknown vote source type")
  }
  val isNotProcessable: Boolean = Instant.ofEpochSecond(update.callbackQuery.message.date.toLong()) < Instant.now().minusSeconds(week)
  val voteValue: VoteValue = VoteValue.valueOf(update.callbackQuery.data)
  val callbackQueryId: String = update.callbackQuery.id

  override fun toString(): String {
    return "VoteUpdate(fromId=$fromId, messageId=$messageId, isFrom=$isFrom, isNotProcessable=$isNotProcessable, voteValue=$voteValue)"
  }
}

open class MemeUpdate(update: Update) {
  val messageId: Int = update.message.messageId
  val caption: String? = update.message.caption
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
