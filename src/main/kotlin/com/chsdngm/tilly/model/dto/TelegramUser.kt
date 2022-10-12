package com.chsdngm.tilly.model.dto

import com.chsdngm.tilly.model.UserStatus
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import java.time.Instant

data class TelegramUser(
    val id: Long,
    val username: String?,
    val firstName: String?,
    val lastName: String?,
    val status: UserStatus = UserStatus.DEFAULT,
    val distributedModerationGroupId: Int? = null,
    var privateModerationLastAssignment: Instant? = null,
) {
    fun mention() = """<a href="tg://user?id=${this.id}">${this.username ?: this.firstName ?: "мутный тип"}</a>"""
}

fun ResultRow.toTelegramUser(): TelegramUser? {
    if (this.getOrNull(TelegramUsers.id) == null) {
        return null
    }

    return TelegramUser(
        id = this[TelegramUsers.id].value,
        username = this[TelegramUsers.username],
        firstName = this[TelegramUsers.firstName],
        lastName = this[TelegramUsers.lastName],
        status = this[TelegramUsers.status],
        distributedModerationGroupId = this[TelegramUsers.distributedModerationGroupId],
        privateModerationLastAssignment = this[TelegramUsers.privateModerationLastAssignment]
    )
}

fun Iterable<ResultRow>.toTelegramUsers(): List<TelegramUser> {
    val iterator = this.iterator()

    val users = mutableListOf<TelegramUser>()

    while (iterator.hasNext()) {
        val user = iterator.next().toTelegramUser() ?: continue
        users.add(user)
    }

    return users
}
fun TelegramUser.toInsertStatement(statement: InsertStatement<Number>): InsertStatement<Number> = statement.also {
    it[TelegramUsers.status] = this.status
    it[TelegramUsers.id] = this.id
    it[TelegramUsers.username] = this.username
    it[TelegramUsers.lastName] = this.lastName
    it[TelegramUsers.firstName] = this.firstName
    it[TelegramUsers.distributedModerationGroupId] = this.distributedModerationGroupId
    it[TelegramUsers.privateModerationLastAssignment] = this.privateModerationLastAssignment
}

fun TelegramUser.toUpdateStatement(statement: UpdateStatement): UpdateStatement = statement.also {
    it[TelegramUsers.firstName] = this.firstName
    it[TelegramUsers.lastName] = this.lastName
    it[TelegramUsers.status] = this.status
    it[TelegramUsers.username] = this.username
    it[TelegramUsers.distributedModerationGroupId] = this.distributedModerationGroupId
    it[TelegramUsers.privateModerationLastAssignment] = this.privateModerationLastAssignment
}