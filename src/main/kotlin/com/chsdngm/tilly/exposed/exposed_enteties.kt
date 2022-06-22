package com.chsdngm.tilly.exposed

import com.chsdngm.tilly.model.MemeStatus
import com.chsdngm.tilly.model.VoteValue
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Query
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

class MemeEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<MemeEntity>(Memes)

    val moderationChatId by Memes.moderationChatId
    val moderationChatMessageId by Memes.moderationChatMessageId
    val senderId by Memes.senderId
    var status by Memes.status
    val privateReplyMessageId: Int? by Memes.privateReplyMessageId
    val fileId by Memes.fileId
    val caption by Memes.caption
    val channelMessageId by Memes.channelMessageId
    val created by Memes.created

    val votes = mutableListOf<Vote>()
}

data class VoteKey(val memeId: Int, val voterId: Int) : Comparable<VoteKey> {
    override fun compareTo(other: VoteKey): Int {
        if (memeId > other.memeId) {
            return 1
        } else if (memeId < other.memeId) {
            return -1
        } else {
            if (voterId > other.voterId) {
                return 1
            } else if (voterId < other.voterId) {
                return -1
            }
        }

        return 0
    }
}

class VoteEntity(id: EntityID<VoteKey>) : Entity<VoteKey>(id) {
    val memeId by Votes.memeId
    val voterId by Votes.voterId
    var sourceChatId by Votes.sourceChatId
    var value by Votes.value
    val created by Votes.created
}

data class Vote(
    val memeId: Int,
    val voterId: Int,
    var sourceChatId: Long,
    var value: VoteValue,
    val created: Instant = Instant.now(),
)

fun ResultRow.toVote(): Vote = Vote(
    memeId = this[Votes.memeId],
    voterId = this[Votes.voterId],
    value = this[Votes.value],
    sourceChatId = this[Votes.sourceChatId],
    created = this[Votes.created]
)

fun Query.toMemeWithVotes(): MemeEntity? {
    if (this.empty()) {
        return null
    }

    val memeEntity = MemeEntity.wrapRow(this.single())
    if (this.single().getOrNull(Votes.memeId) != null) {
        memeEntity.votes += this.map { it.toVote() }
    }

    return memeEntity
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