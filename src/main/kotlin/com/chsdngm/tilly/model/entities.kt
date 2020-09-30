package com.chsdngm.tilly.model

import org.hibernate.annotations.Type
import java.io.Serializable
import javax.persistence.*

@Entity
data class Meme(
    val moderationChatId: Long,
    val moderationChatMessageId: Int,
    val senderId: Int,
    val privateReplyMessageId: Int?,
    val fileId: String,
    val caption: String?,
    var channelMessageId: Int? = null,

    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY, mappedBy = "memeId", orphanRemoval = true)
    val votes: MutableSet<Vote> = mutableSetOf()) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Int = 0

  override fun toString(): String {
    return "Meme(moderationChatId=$moderationChatId, moderationChatMessageId=$moderationChatMessageId, senderId=$senderId, senderMessageId=$privateReplyMessageId, caption=$caption, channelMessageId=$channelMessageId, id=$id)"
  }
}

@Entity
data class TelegramUser(
    @Id val id: Int,
    val username: String?,
    val firstName: String?,
    val lastName: String?) {
  fun mention() = """<a href="tg://user?id=${this.id}">${this.username ?: this.firstName ?: "мутный тип"}</a>"""
}

@Entity
@IdClass(Vote.VoteKey::class)
data class Vote(
    @Id val memeId: Int,
    @Id val voterId: Int,
    val sourceChatId: Long,
    @Enumerated(EnumType.STRING)
    var value: VoteValue) {

  @Embeddable
  class VoteKey(
      val memeId: Int,
      val voterId: Int) : Serializable
}

@Entity
data class Image(
    @Id val fileId: String,
    @Lob @Type(type = "org.hibernate.type.BinaryType") val file: ByteArray) {

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
