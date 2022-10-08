package com.chsdngm.tilly.model.dto

import org.jetbrains.exposed.sql.statements.InsertStatement

data class DistributedModerationEvent(
    val memeId: Int,
    val moderatorId: Long,
    val chatMessageId: Int,
    val moderationGroupId: Int
)

fun DistributedModerationEvent.toInsertStatement(statement: InsertStatement<Number>): InsertStatement<Number> =
    statement.also {
        it[DistributedModerationEvents.memeId] = memeId
        it[DistributedModerationEvents.moderatorId] = moderatorId
        it[DistributedModerationEvents.chatMessageId] = chatMessageId
        it[DistributedModerationEvents.moderationGroupId] = moderationGroupId
    }