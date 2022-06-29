package com.chsdngm.tilly.exposed

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Repository

@Repository
class MemeDao(val database: Database) {
    fun findMemeByChannelMessageId(channelMessageId: Int): Meme? = transaction {
        (Memes leftJoin Votes)
            .select(Memes.channelMessageId eq channelMessageId)
            .toList()
            .toMeme()
    }

    fun findMemeByModerationChatIdAndModerationChatMessageId(
        moderationChatId: Long,
        moderationChatMessageId: Int,
    ): Meme? = transaction {
         Memes.leftJoin(Votes)
            .select((Memes.moderationChatId eq moderationChatId) and (Memes.moderationChatMessageId eq moderationChatMessageId))
            .toList()
            .toMeme()
    }
}

