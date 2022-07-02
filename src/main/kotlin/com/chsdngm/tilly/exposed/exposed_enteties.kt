package com.chsdngm.tilly.exposed

import com.chsdngm.tilly.model.MemeStatus
import com.chsdngm.tilly.model.VoteValue
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import java.time.Instant

object Memes : IntIdTable("meme", "id") {
    val moderationChatId = long("moderation_chat_id")
    val moderationChatMessageId = integer("moderation_chat_message_id")
    val senderId = integer("sender_id")
    val status = enumerationByName("status", 10, MemeStatus::class)
    val privateReplyMessageId = integer("private_reply_message_id").nullable()
    val fileId = text("file_id")
    val caption = text("caption").nullable()
    val channelMessageId = integer("channel_message_id").nullable()
    val created = timestamp("created")
}

object Votes : Table("vote") {
    val memeId = integer("meme_id").references(Memes.id)
    val voterId = integer("voter_id")
    val sourceChatId = long("source_chat_id")
    val value = enumerationByName("value", 10, VoteValue::class)
    val created = timestamp("created")
}

data class Vote(
    val memeId: Int,
    val voterId: Int,
    var sourceChatId: Long,
    var value: VoteValue,
    val created: Instant = Instant.now(),
)

data class Meme(
    val id: Int,
    val moderationChatId: Long,
    val moderationChatMessageId: Int,
    val senderId: Int,
    var status: MemeStatus,
    val privateReplyMessageId: Int?,
    val fileId: String,
    val caption: String?,
    var channelMessageId: Int? = null,
    var created: Instant,
    val votes: MutableList<Vote> = mutableListOf()
)

fun ResultRow.toVote(): Vote? {
    if (this.getOrNull(Votes.memeId) == null || this.getOrNull(Votes.voterId) == null) {
        return null
    }

    return Vote(
        memeId = this[Votes.memeId],
        voterId = this[Votes.voterId],
        value = this[Votes.value],
        sourceChatId = this[Votes.sourceChatId],
        created = this[Votes.created]
    )
}

fun ResultRow.toMeme(): Meme? {
    if (this.getOrNull(Memes.id) == null) {
        return null
    }

    return Meme(
        id = this[Memes.id].value,
        moderationChatId = this[Memes.moderationChatId],
        moderationChatMessageId = this[Memes.moderationChatMessageId],
        created = this[Memes.created],
        channelMessageId = this[Memes.channelMessageId],
        caption = this[Memes.caption],
        fileId = this[Memes.fileId],
        privateReplyMessageId = this[Memes.privateReplyMessageId],
        senderId = this[Memes.senderId],
        status = this[Memes.status]
    )
}

fun Iterable<ResultRow>.toMeme(): Meme? {
    return if (this.count() == 0) {
        null
    } else {
        val meme = this.first().toMeme()

        this.forEach {
            it.toVote()?.let { it1 -> meme?.votes?.add(it1) }
        }

        meme
    }
}

fun Vote.toInsertStatement(statement: InsertStatement<Number>): InsertStatement<Number> = statement.also {
    it[Votes.voterId] = this.voterId
    it[Votes.memeId] = this.memeId
    it[Votes.value] = this.value
    it[Votes.sourceChatId] = this.sourceChatId
    it[Votes.created] = this.created
}

fun Vote.toUpdateStatement(statement: UpdateStatement): UpdateStatement = statement.also {
    it[Votes.value] = this.value
    it[Votes.sourceChatId] = this.sourceChatId
    it[Votes.created] = this.created
}

fun Meme.toUpdateStatement(statement: UpdateStatement): UpdateStatement = statement.also {
    it[Memes.status] = this.status
    it[Memes.channelMessageId] = this.channelMessageId
}