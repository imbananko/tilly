package com.chsdngm.tilly.model

import com.chsdngm.tilly.config.TelegramConfig.Companion.CHANNEL_ID
import com.chsdngm.tilly.config.TelegramConfig.Companion.CHAT_ID
import com.chsdngm.tilly.utility.mention
import com.chsdngm.tilly.utility.minusDays
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import java.io.File
import java.time.Instant

private val trashCaptionParts = listOf("sender", "photo from")

class VoteUpdate(update: Update) {
    val voterId: Long = update.callbackQuery.from.id
    val messageId: Int = update.callbackQuery.message.messageId
    val isFrom: String = when {
        update.callbackQuery.message.isChannelMessage && update.callbackQuery.message.chatId.toString() == CHANNEL_ID -> CHANNEL_ID
        update.callbackQuery.message.isSuperGroupMessage && update.callbackQuery.message.chatId.toString() == CHAT_ID -> CHAT_ID
        else -> throw Exception("Unknown vote source type")
    }
    val isOld: Boolean = Instant.ofEpochSecond(update.callbackQuery.message.date.toLong()) < Instant.now().minusDays(7)
    val voteValue: VoteValue = VoteValue.valueOf(update.callbackQuery.data)
    val callbackQueryId: String = update.callbackQuery.id
    val timestampMs = System.currentTimeMillis()

    override fun toString(): String {
        return "VoteUpdate(fromId=$voterId, messageId=$messageId, isFrom=$isFrom, voteValue=$voteValue)"
    }
}

abstract class MemeUpdate(
    open val messageId: Int,
    open val fileId: String,
    open val user: User,
    caption: String?
) {

    val caption: String? = caption?.takeIf { caption ->
        val lowerCaseCaption = caption.lowercase()
        !trashCaptionParts.any { lowerCaseCaption.contains(it) }
    }

    val status: MemeStatus = if (caption?.contains("#local") == true) MemeStatus.LOCAL
    else MemeStatus.MODERATION

    var isFreshman: Boolean = false

    lateinit var file: File
    override fun toString(): String {
        return "MemeUpdate(messageId=$messageId, user='${user.mention()}, caption=$caption, status=$status, isFreshman=$isFreshman)"
    }
}

class UserMemeUpdate(
    override val messageId: Int,
    override val fileId: String,
    override val user: User,
    caption: String?
) : MemeUpdate(messageId, fileId, user, caption) {
    constructor(update: Update) : this(
        update.message.messageId,
        update.message.photo.maxByOrNull { it.fileSize }!!.fileId,
        update.message.from,
        update.message.caption?.takeIf { caption ->
            val lowerCaseCaption = caption.lowercase()
            !trashCaptionParts.any { lowerCaseCaption.contains(it) }
        }
    )
}

class AutoSuggestedMemeUpdate(
    override val messageId: Int,
    override val fileId: String,
    override val user: User,
    caption: String?
) : MemeUpdate(
    messageId,
    fileId,
    user,
    caption
) {
    constructor(update: AutosuggestionVoteUpdate) : this(update.messageId, update.fileId, update.whoSuggests, null)
}

class CommandUpdate(update: Update) {
    val senderId: String = update.message.chatId.toString()
    val chatId: String = update.message.chatId.toString()
    val messageId: Int = update.message.messageId
    val value: Command? = Command.from(update.message.text.split(' ').first())
    val text: String = update.message.text

    enum class Command(val value: String) {
        STATS("/stats"),
        HELP("/help"),
        START("/start"),
        CONFIG("/config");

        companion object {
            private val map = values().associateBy(Command::value)
            fun from(value: String) = map[value]
        }
    }

    override fun toString(): String {
        return "CommandUpdate(senderId=$senderId, value=$value)"
    }
}

class InlineCommandUpdate(update: Update) {
    val id: String = update.inlineQuery.id
    val value: String = update.inlineQuery.query
    val offset: String = update.inlineQuery.offset
    override fun toString(): String {
        return "InlineCommandUpdate(id='$id', value='$value', offset='$offset')"
    }
}

class PrivateVoteUpdate(update: Update) {
    val user: User = update.callbackQuery.from
    val messageId: Int = update.callbackQuery.message.messageId
    val voteValue: PrivateVoteValue = PrivateVoteValue.valueOf(update.callbackQuery.data)
    override fun toString(): String {
        return "PrivateVoteUpdate(user=$user, messageId=$messageId, voteValue=$voteValue)"
    }
}

class DistributedModerationVoteUpdate(update: Update) {
    val user: User = update.callbackQuery.from
    val messageId: Int = update.callbackQuery.message.messageId
    val voteValue: DistributedModerationVoteValue = DistributedModerationVoteValue.valueOf(update.callbackQuery.data)
    override fun toString(): String {
        return "DistributedModerationVoteUpdate(user=$user, messageId=$messageId, voteValue=$voteValue)"
    }
}

class AutosuggestionVoteUpdate(update: Update) {
    private val approverName: String = update.callbackQuery.from.mention()

    val whoSuggests: User = update.callbackQuery.message.from
    val fileId: String = update.callbackQuery.message.photo.maxByOrNull { it.fileSize }!!.fileId
    val chatId: Long = update.callbackQuery.message.chatId
    val messageId: Int = update.callbackQuery.message.messageId
    val voteValue: AutosuggestionVoteValue = AutosuggestionVoteValue.valueOf(update.callbackQuery.data)

    override fun toString(): String {
        return "AutosuggestionVoteUpdate(approver=$approverName, whoSuggests=${whoSuggests.mention()}, groupId=$chatId, messageId=$messageId, voteValue=$voteValue)"
    }
}