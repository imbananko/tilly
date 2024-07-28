package com.chsdngm.tilly.model.dto

import com.chsdngm.tilly.model.InstagramReelStatus
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import java.time.Instant

data class InstagramReel(
    val id: Int = 0,
    val url: String,
    var postId: String? = null,
    var moderationChatId: Long? = null,
    var moderationChatMessageId: Int? = null,
    val senderId: Long,
    var status: InstagramReelStatus,
    val privateReplyMessageId: Int? = null,
    val fileId: String? = null,
    var channelMessageId: Int? = null,
    val created: Instant = Instant.now(),
    var published: Instant? = null
)

fun InstagramReel.toInsertStatement(statement: InsertStatement<Number>) = statement.also {
    it[InstagramReels.url] = this.url
    it[InstagramReels.postId] = this.postId
    it[InstagramReels.senderId] = this.senderId
    it[InstagramReels.status] = this.status
    it[InstagramReels.created] = this.created
}

fun InstagramReel.toUpdateStatement(statement: UpdateStatement): UpdateStatement = statement.also {
    it[InstagramReels.url] = this.url
    it[InstagramReels.postId] = this.postId
    it[InstagramReels.moderationChatId] = this.moderationChatId
    it[InstagramReels.moderationChatMessageId] = this.moderationChatMessageId
    it[InstagramReels.senderId] = this.senderId
    it[InstagramReels.privateReplyMessageId] = this.privateReplyMessageId
    it[InstagramReels.fileId] = this.fileId
    it[InstagramReels.channelMessageId] = this.channelMessageId
    it[InstagramReels.created] = this.created
    it[InstagramReels.published] = this.published
    it[InstagramReels.status] = this.status
}

fun ResultRow.toInstagramReel(): InstagramReel? {
    if (this.getOrNull(InstagramReels.id) == null) {
        return null
    }

    return InstagramReel(
        id = this[InstagramReels.id].value,
        url = this[InstagramReels.url],
        postId = this[InstagramReels.postId],
        moderationChatId = this[InstagramReels.moderationChatId],
        moderationChatMessageId = this[InstagramReels.moderationChatMessageId],
        created = this[InstagramReels.created],
        channelMessageId = this[InstagramReels.channelMessageId],
        fileId = this[InstagramReels.fileId],
        privateReplyMessageId = this[InstagramReels.privateReplyMessageId],
        senderId = this[InstagramReels.senderId],
        status = this[InstagramReels.status],
    )
}

fun Iterable<ResultRow>.toInstagramReel(): InstagramReel? {
    val iterator = this.iterator()

    if (!iterator.hasNext()) {
        return null
    }

    return iterator.next().toInstagramReel()
}

fun Iterable<ResultRow>.toInstagramReelsWithVotes(): Map<InstagramReel, List<Vote>> {
    val iterator = this.iterator()

    if (!iterator.hasNext()) {
        return mapOf()
    }

    val reels = linkedMapOf<InstagramReel, MutableList<Vote>>()

    while (iterator.hasNext()) {
        val current = iterator.next()
        val reel = current.toInstagramReel() ?: continue
        val vote = current.toVote()

        if (vote == null) {
            reels[reel] = mutableListOf()
            continue
        }

        val votes = reels[reel]

        if (votes != null) {
            votes.add(vote)
        } else {
            reels[reel] = mutableListOf(vote)
        }
    }

    return reels
}

fun Iterable<ResultRow>.toInstagramReels(): List<InstagramReel> {
    val iterator = this.iterator()

    val instagramReels = mutableListOf<InstagramReel>()

    while (iterator.hasNext()) {
        val meme = iterator.next().toInstagramReel() ?: continue
        instagramReels.add(meme)
    }

    return instagramReels
}