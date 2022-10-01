package com.chsdngm.tilly.model.dto

import com.chsdngm.tilly.model.MemeStatus
import com.chsdngm.tilly.model.VoteValue
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.BasicBinaryColumnType
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object Memes : IntIdTable("meme", "id") {
    val moderationChatId = long("moderation_chat_id")
    val moderationChatMessageId = integer("moderation_chat_message_id")
    val senderId = long("sender_id")
    val status = enumerationByName("status", 10, MemeStatus::class)
    val privateReplyMessageId = integer("private_reply_message_id").nullable()
    val fileId = text("file_id")
    val caption = text("caption").nullable()
    val channelMessageId = integer("channel_message_id").nullable()
    val created = timestamp("created")
}

object MemesLogs : Table("meme_log") {
    val memeId = integer("meme_id")
    val chatId = long("chat_id")
    val messageId = integer("message_id")
    val created = timestamp("created")
}

object Votes : Table("vote") {
    val memeId = integer("meme_id").references(Memes.id)
    val voterId = long("voter_id")
    val sourceChatId = long("source_chat_id")
    val value = enumerationByName("value", 10, VoteValue::class)
    val created = timestamp("created")
}

object Images : Table("image") {
    val fileId = text("file_id")
    val file = binaryCustomLogging("file")
    val hash = binaryCustomLogging("hash")
    val rawText = text("raw_text").nullable()
    val rawLabels = text("raw_labels").nullable()

    private fun binaryCustomLogging(name: String): Column<ByteArray> = registerColumn(name, BasicBinaryColumnTypeCustomLogging)
}

object BasicBinaryColumnTypeCustomLogging : BasicBinaryColumnType() {
    override fun valueToString(value: Any?): String {
        return "bytes:${(value as ByteArray).size}"
    }
}