package com.chsdngm.tilly.exposed

import com.chsdngm.tilly.model.MemeStatus
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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

    fun insert(meme: Meme) = transaction {
        Memes.insert { meme.toInsertStatement(it) }.resultedValues?.first()?.toMeme()
            ?: throw NoSuchElementException("Error saving meme")
    }

    fun update(meme: Meme) = transaction {
        Memes.update({ Memes.id eq meme.id }) { meme.toUpdateStatement(it) }
    }

    fun findFirstByStatusOrderByCreated(memeStatus: MemeStatus): Meme? = transaction {
        (Memes leftJoin Votes)
            .select { Memes.status eq memeStatus }.orderBy(Memes.created).toList().toMeme()
    }

    fun findByFileId(fileId: String): Meme? = transaction {
        Memes.select { Memes.fileId eq fileId }.orderBy(Memes.created).singleOrNull()?.toMeme()
    }
}

