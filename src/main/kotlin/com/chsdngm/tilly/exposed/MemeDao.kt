package com.chsdngm.tilly.exposed

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Repository

@Repository
class MemeDao(val database: Database) {
    fun findMemeByChannelMessageId(channelMessageId: Int): MemeEntity? = transaction {
         (Memes leftJoin Votes)
            .select(Memes.channelMessageId eq channelMessageId)
             .toMemeWithVotes()
    }

    fun findMemeByModerationChatIdAndModerationChatMessageId(
        moderationChatId: Long,
        moderationChatMessageId: Int,
    ): MemeEntity? = transaction {
        (Memes leftJoin Votes)
            .select((Memes.moderationChatId eq moderationChatId) and (Memes.moderationChatMessageId eq moderationChatMessageId))
            .toMemeWithVotes()
    }
}
