package com.chsdngm.tilly.model.dto

import com.chsdngm.tilly.model.MemeStatus
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import java.time.Instant

data class Meme(
    val moderationChatId: Long,
    val moderationChatMessageId: Int,
    val senderId: Int,
    var status: MemeStatus,
    val privateReplyMessageId: Int?,
    val fileId: String,
    val caption: String?,
    var channelMessageId: Int? = null,
    val created: Instant = Instant.now(),
    val id: Int = 0,
) {
    val votes: MutableList<Vote> = mutableListOf()
}

fun Meme.toInsertStatement(statement: InsertStatement<Number>): InsertStatement<Number> = statement.also {
    it[Memes.status] = this.status
    it[Memes.channelMessageId] = this.channelMessageId
    it[Memes.caption] = this.caption
    it[Memes.fileId] = this.fileId
    it[Memes.moderationChatId] = this.moderationChatId
    it[Memes.privateReplyMessageId] = this.privateReplyMessageId
    it[Memes.senderId] = this.senderId
    it[Memes.moderationChatMessageId] = this.moderationChatMessageId
    it[Memes.created] = this.created
}

fun Meme.toUpdateStatement(statement: UpdateStatement): UpdateStatement = statement.also {
    it[Memes.status] = this.status
    it[Memes.channelMessageId] = this.channelMessageId
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
    val iterator = this.iterator()

    if (!iterator.hasNext()) {
        return null
    }

    var current = iterator.next()
    val meme = current.toMeme() ?: return null
    current.toVote()?.let { meme.votes.add(it) }

    while (iterator.hasNext()) {
        current = iterator.next()
        val vote = current.toVote()
        if (vote == null || vote.memeId != meme.id) {
            return meme
        }

        meme.votes.add(vote)
    }

    return meme
}

fun Iterable<ResultRow>.toMemes(): List<Meme> {
    val iterator = this.iterator()

    if (!iterator.hasNext()) {
        return listOf()
    }

    val memes = mutableListOf<Meme>()
    var currentMeme: Meme? = null

    while (iterator.hasNext()) {
        val current = iterator.next()

        val meme = current.toMeme() ?: return memes
        if (meme.id != currentMeme?.id) {
            currentMeme = meme
            memes.add(currentMeme)
        }

        val vote = current.toVote()
        if (vote != null) {
            currentMeme.votes.add(vote)
        }
    }

    return memes
}
