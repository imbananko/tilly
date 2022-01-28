package com.chsdngm.tilly.model

import com.vladmihalcea.hibernate.type.array.ListArrayType
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import java.io.Serializable
import java.time.Instant
import java.util.*
import javax.persistence.*

@Entity
data class PrivateModerator(
    @Id val userId: Int,
    val assigned: Date
)

@Entity
data class Meme(
    val moderationChatId: Long,
    val moderationChatMessageId: Int,
    val senderId: Int,
    @Enumerated(EnumType.STRING)
    var status: MemeStatus,
    val privateReplyMessageId: Int?,
    val fileId: String,
    val caption: String?,
    var channelMessageId: Int? = null,
    var created: Instant = Instant.now(),

    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY, mappedBy = "memeId", orphanRemoval = true)
    val votes: MutableSet<Vote> = mutableSetOf()
) {

    @Id
    @GeneratedValue(strategy = GenerationType.TABLE)
    val id: Int = 0

    override fun toString(): String {
        return "Meme(moderationChatId=$moderationChatId, moderationChatMessageId=$moderationChatMessageId, senderId=$senderId, senderMessageId=$privateReplyMessageId, caption=$caption, channelMessageId=$channelMessageId, id=$id, status=$status)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Meme

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id
    }
}

@Entity
data class TelegramUser(
    @Id val id: Int,
    val username: String?,
    val firstName: String?,
    val lastName: String?,
    @Enumerated(EnumType.STRING)
    val status: UserStatus
) {
    fun mention() = """<a href="tg://user?id=${this.id}">${this.username ?: this.firstName ?: "мутный тип"}</a>"""
}

@Entity
@IdClass(Vote.VoteKey::class)
data class Vote(
    @Id val memeId: Int,
    @Id val voterId: Int,
    var sourceChatId: Long,
    @Enumerated(EnumType.STRING)
    var value: VoteValue,
    var created: Instant = Instant.now(),
) {

    @Embeddable
    class VoteKey(
        val memeId: Int,
        val voterId: Int,
    ) : Serializable

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Vote

        if (memeId != other.memeId) return false
        if (voterId != other.voterId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = memeId
        result = 31 * result + voterId
        return result
    }
}

@Entity
@TypeDef(name = "list-array", typeClass = ListArrayType::class)
data class Image(
    @Id val fileId: String,
    @Lob @Type(type = "org.hibernate.type.BinaryType") val file: ByteArray,
    @Type(type = "list-array") @Column(columnDefinition = "text[]") var words: List<String>? = null,
    @Type(type = "list-array") @Column(columnDefinition = "text[]") var labels: List<String>? = null,
    @Lob @Type(type = "org.hibernate.type.BinaryType") val hash: ByteArray,
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