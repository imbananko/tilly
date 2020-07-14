package com.chsdngm.tilly.model

import org.hibernate.annotations.Type
import java.io.Serializable
import javax.persistence.*

@Entity
data class Meme(
    @Id val chatMessageId: Int,
    val senderId: Int,
    val fileId: String,
    val caption: String?,
    val privateMessageId: Int?,
    val moderationChatId: Long,
    val channelMessageId: Int? = null,
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY, mappedBy = "chatMessageId", orphanRemoval = true)
    val votes: MutableList<Vote> = mutableListOf()) {

  override fun toString(): String {
    return "Meme(chatMessageId=$chatMessageId, senderId=$senderId, caption=$caption, privateMessageId=$privateMessageId, votes=$votes)"
  }
}

@Entity
data class TelegramUser(
    @Id val id: Int,
    val username: String?,
    val firstName: String?,
    val lastName: String?
)

@Entity
@IdClass(Vote.VoteKey::class)
data class Vote(
    @Id val chatMessageId: Int,
    @Id val voterId: Int,
    @Enumerated(EnumType.STRING) val value: VoteValue,
    @Enumerated(EnumType.STRING) val source: VoteSourceType
) {

  @Embeddable
  data class VoteKey(
      val chatMessageId: Int,
      val voterId: Int) : Serializable
}

@Entity
data class Image(
    @Id val fileId: String,
    @Lob @Type(type = "org.hibernate.type.BinaryType") val file: ByteArray
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