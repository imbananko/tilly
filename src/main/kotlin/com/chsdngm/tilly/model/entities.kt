package com.chsdngm.tilly.model

import java.util.*
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Id

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
