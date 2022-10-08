package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.dto.DistributedModerationEvent
import com.chsdngm.tilly.model.dto.DistributedModerationEvents
import com.chsdngm.tilly.model.dto.toInsertStatement
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Repository

@Repository
class DistributedModerationEventDao {
    fun insert(event: DistributedModerationEvent) = transaction {
        DistributedModerationEvents.insert { event.toInsertStatement(it) }
    }

    fun findMemeId(moderatorId: Long, chatMessageId: Int): Int? = transaction {
        DistributedModerationEvents
                .slice(DistributedModerationEvents.memeId)
                .select((DistributedModerationEvents.moderatorId eq moderatorId) and (DistributedModerationEvents.chatMessageId eq chatMessageId))
                .singleOrNull()
                ?.getOrNull(DistributedModerationEvents.memeId)

    }

}