package com.chsdngm.tilly.model

import com.vladmihalcea.hibernate.type.array.ListArrayType
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import java.util.*
import javax.persistence.*

@Entity
data class PrivateModerator(
    @Id val userId: Int,
    val assigned: Date
)

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