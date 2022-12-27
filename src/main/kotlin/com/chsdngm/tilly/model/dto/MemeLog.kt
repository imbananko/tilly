package com.chsdngm.tilly.model.dto

import org.jetbrains.exposed.sql.statements.InsertStatement
import java.time.Instant

data class MemeLog(
    val memeId: Int,
    val chatId: Long?,
    val messageId: Int?,
    val created: Instant = Instant.now(),
    val memeCreated: Instant,
) {
    companion object {
        fun fromMeme(meme: Meme) = MemeLog(meme.id, meme.moderationChatId, meme.moderationChatMessageId, memeCreated = meme.created)
    }
}

fun MemeLog.toInsertStatement(statement: InsertStatement<Number>): InsertStatement<Number> = statement.also {
    it[MemesLogs.memeId] = this.memeId
    it[MemesLogs.messageId] = this.messageId
    it[MemesLogs.chatId] = this.chatId
    it[MemesLogs.created] = this.created
    it[MemesLogs.memeCreated] = this.memeCreated
}