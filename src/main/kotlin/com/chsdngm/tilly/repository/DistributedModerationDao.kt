package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.dto.DistributedModerationEvent
import com.chsdngm.tilly.model.dto.Votes
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Repository

@Repository
class DistributedModerationDao {
    fun createEvent(memeId: Int, moderatorId: Long, chatMessageId: Int, moderationGroupId: Int) = transaction {
        DistributedModerationEvent.insert { row ->
            row[DistributedModerationEvent.memeId] = memeId
            row[DistributedModerationEvent.moderatorId] = moderatorId
            row[DistributedModerationEvent.chatMessageId] = chatMessageId
            row[DistributedModerationEvent.moderationGroupId] = moderationGroupId
        }
    }

    fun findMemeId(moderatorId: Long, chatMessageId: Int): Int? = transaction {
        DistributedModerationEvent
                .slice(DistributedModerationEvent.memeId)
                .select((DistributedModerationEvent.moderatorId eq moderatorId) and (DistributedModerationEvent.chatMessageId eq chatMessageId))
                .singleOrNull()
                ?.getOrNull(DistributedModerationEvent.memeId)

    }

}