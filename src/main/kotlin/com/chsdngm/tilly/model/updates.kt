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

abstract class Timestampable {
    val createdAt: Long = System.currentTimeMillis()
}

class VoteUpdate(update: Update) : Timestampable() {
    val voterId: Long = update.callbackQuery.from.id
    val messageId: Int = update.callbackQuery.message.messageId
    val sourceChatId: String = when {
        update.callbackQuery.message.isChannelMessage && update.callbackQuery.message.chatId.toString() == CHANNEL_ID -> CHANNEL_ID
        update.callbackQuery.message.isSuperGroupMessage && update.callbackQuery.message.chatId.toString() == CHAT_ID -> CHAT_ID
        else -> throw IllegalArgumentException("Unknown sourceChatId, update=$update")
    }
    val isOld: Boolean = Instant.ofEpochSecond(update.callbackQuery.message.date.toLong()) < Instant.now().minusDays(7)
    val voteValue: VoteValue = VoteValue.valueOf(update.callbackQuery.data)
    val callbackQueryId: String = update.callbackQuery.id

    override fun toString(): String {
        return "VoteUpdate(fromId=$voterId, messageId=$messageId, sourceChatId=$sourceChatId, voteValue=$voteValue)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VoteUpdate

        if (voterId != other.voterId) return false
        if (messageId != other.messageId) return false
        if (sourceChatId != other.sourceChatId) return false
        if (voteValue != other.voteValue) return false

        return true
    }

    override fun hashCode(): Int {
        var result = voterId.hashCode()
        result = 31 * result + messageId
        result = 31 * result + sourceChatId.hashCode()
        result = 31 * result + voteValue.hashCode()
        return result
    }
}

abstract class MemeUpdate(
    open val messageId: Int,
    open val fileId: String,
    open val user: User,
    caption: String?
) : Timestampable() {

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MemeUpdate

        if (messageId != other.messageId) return false
        if (fileId != other.fileId) return false
        if (user != other.user) return false

        return true
    }

    override fun hashCode(): Int {
        var result = messageId
        result = 31 * result + fileId.hashCode()
        result = 31 * result + user.hashCode()
        return result
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

class CommandUpdate(update: Update) : Timestampable() {
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CommandUpdate

        if (senderId != other.senderId) return false
        if (chatId != other.chatId) return false
        if (messageId != other.messageId) return false
        if (text != other.text) return false

        return true
    }

    override fun hashCode(): Int {
        var result = senderId.hashCode()
        result = 31 * result + chatId.hashCode()
        result = 31 * result + messageId
        result = 31 * result + text.hashCode()
        return result
    }
}

class InlineCommandUpdate(update: Update) : Timestampable() {
    val id: String = update.inlineQuery.id
    val value: String = update.inlineQuery.query
    val offset: String = update.inlineQuery.offset
    override fun toString(): String {
        return "InlineCommandUpdate(id='$id', value='$value', offset='$offset')"
    }
}

class PrivateVoteUpdate(update: Update) : Timestampable() {
    val user: User = update.callbackQuery.from
    val messageId: Int = update.callbackQuery.message.messageId
    val voteValue: PrivateVoteValue = PrivateVoteValue.valueOf(update.callbackQuery.data)
    override fun toString(): String {
        return "PrivateVoteUpdate(user=$user, messageId=$messageId, voteValue=$voteValue)"
    }
}

class AutosuggestionVoteUpdate(update: Update) : Timestampable() {
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