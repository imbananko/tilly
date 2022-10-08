package com.chsdngm.tilly.model.dto

import com.chsdngm.tilly.model.MemeStatus
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import java.time.Instant

data class Meme(
    val moderationChatId: Long?,
    val moderationChatMessageId: Int?,
    val senderId: Long,
    var status: MemeStatus,
    val privateReplyMessageId: Int?,
    val fileId: String,
    val caption: String?,
    var channelMessageId: Int? = null,
    val created: Instant = Instant.now(),
    val id: Int = 0,
)

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
    it[Memes.moderationChatId] = this.moderationChatId
    it[Memes.moderationChatMessageId] = this.moderationChatMessageId
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

fun Iterable<ResultRow>.toMemeWithVotes(): Pair<Meme, List<Vote>>? {
    val iterator = this.iterator()

    if (!iterator.hasNext()) {
        return null
    }

    val first = iterator.next()
    val meme = first.toMeme() ?: return null
    val votes = mutableListOf<Vote>()
    first.toVote()?.let { votes.add(it) }

    while (iterator.hasNext()) {
        val vote = iterator.next().toVote()
        if (vote == null || vote.memeId != meme.id) {
            continue
        }

        votes.add(vote)
    }

    return meme to votes
}

fun Iterable<ResultRow>.toMeme(): Meme? {
    val iterator = this.iterator()

    if (!iterator.hasNext()) {
        return null
    }

    return iterator.next().toMeme()
}

fun Iterable<ResultRow>.toMemesWithVotes(): Map<Meme, List<Vote>> {
    val iterator = this.iterator()

    if (!iterator.hasNext()) {
        return mapOf()
    }

    val memes = linkedMapOf<Meme, MutableList<Vote>>()

    while (iterator.hasNext()) {
        val current = iterator.next()
        val meme = current.toMeme() ?: continue
        val vote = current.toVote()

        if (vote == null) {
            memes[meme] = mutableListOf()
            continue
        }

        val votes = memes[meme]

        if (votes != null) {
            votes.add(vote)
        } else {
            memes[meme] = mutableListOf(vote)
        }
    }

    return memes
}

fun Iterable<ResultRow>.toMemes(): List<Meme> {
    val iterator = this.iterator()

    val memes = mutableListOf<Meme>()

    while (iterator.hasNext()) {
        val meme = iterator.next().toMeme() ?: continue
        memes.add(meme)
    }

    return memes
}