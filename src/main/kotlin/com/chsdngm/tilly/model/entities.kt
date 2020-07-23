package com.chsdngm.tilly.model

import org.hibernate.annotations.Type
import java.io.Serializable
import javax.persistence.*

@Entity
@IdClass(Meme.MemeKey::class)
data class Meme(
    @Id val moderationChatId: Long,
    @Id val chatMessageId: Int,
    val senderId: Int,
    val fileId: String,
    val caption: String?,
    val privateMessageId: Int?,
    val channelMessageId: Int? = null,
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumns(
        JoinColumn(name = "moderationChatId", referencedColumnName = "moderationChatId", nullable = false, insertable = false, updatable = false),
        JoinColumn(name = "chatMessageId", referencedColumnName = "chatMessageId", nullable = false, insertable = false, updatable = false)
    )
    val votes: MutableList<Vote> = mutableListOf()) {

  @Embeddable
  data class MemeKey(
      val moderationChatId: Long,
      val chatMessageId: Int) : Serializable

  override fun toString(): String {
    return "Meme(moderationChatId=$moderationChatId, chatMessageId=$chatMessageId, senderId=$senderId, caption=$caption, privateMessageId=$privateMessageId, votes=$votes)"
  }
}

@Entity
data class TelegramUser(
    @Id val id: Int,
    val username: String?,
    val firstName: String?,
    val lastName: String?
) {
  fun mention() = """<a href="tg://user?id=${this.id}">${this.username ?: this.firstName ?: "мутный тип"}</a>"""
}

@Entity
@IdClass(Vote.VoteKey::class)
data class Vote(
    @Id val moderationChatId: Long,
    @Id val chatMessageId: Int,
    @Id val voterId: Int,
    @Enumerated(EnumType.STRING) val value: VoteValue,
    @Enumerated(EnumType.STRING) val source: VoteSourceType
) {

  @Embeddable
  data class VoteKey(
      val moderationChatId: Long,
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
