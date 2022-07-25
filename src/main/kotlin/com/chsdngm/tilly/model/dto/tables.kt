package com.chsdngm.tilly.model.dto

import com.chsdngm.tilly.model.MemeStatus
import com.chsdngm.tilly.model.VoteValue
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement

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