package com.chsdngm.tilly.model

import com.chsdngm.tilly.utility.TillyConfig
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHANNEL_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHAT_ID
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

    override fun toString(): String {
        return "VoteUpdate(fromId=$voterId, messageId=$messageId, isFrom=$isFrom, voteValue=$voteValue)"
    }
}

interface MemeUpdate {
    val messageId: Int
    val caption: String?
    val fileId: String
    val user: User
    val senderName: String
    val status: MemeStatus

    var file: File
    var isFreshman: Boolean

    val isByTillyBot: Boolean
        get() = user.id == TillyConfig.BOT_ID
}

class UserMemeUpdate(update: Update) : MemeUpdate {
    override val messageId: Int = update.message.messageId
    override val caption: String? = update.message.caption?.takeIf { caption ->
        val lowerCaseCaption = caption.lowercase()
        !trashCaptionParts.any { lowerCaseCaption.contains(it) }
    }
    override val fileId: String = update.message.photo.maxByOrNull { it.fileSize }!!.fileId
    override val user: User = update.message.from
    override val senderName: String = update.message.from.mention()
    override val status: MemeStatus =
        if (caption?.contains("#local") == true) MemeStatus.LOCAL
        else MemeStatus.MODERATION

    override lateinit var file: File
    override var isFreshman: Boolean = false

    override fun toString(): String {
        return "UserMemeUpdate(chatMessageId=$messageId, caption=$caption, fileId='$fileId', user='${user.id})"
    }
}

class AutoSuggestedMemeUpdate(update: AutosuggestionVoteUpdate) : MemeUpdate {
    override val messageId: Int = update.messageId
    override val caption: String? = null
    override val fileId: String = update.fileId
    override val user: User = update.whoSuggests
    override val senderName: String = update.whoSuggests.mention()
    override val status: MemeStatus = MemeStatus.MODERATION

    override lateinit var file: File
    override var isFreshman: Boolean = false

    override fun toString(): String {
        return "AutoSuggestedMemeUpdate(chatMessageId=$messageId, caption=$caption, fileId='$fileId', user='${user.id})"
    }
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

class AutosuggestionVoteUpdate(update: Update) {
    val approver: User = update.callbackQuery.from
    val whoSuggests: User = update.callbackQuery.message.from
    val fileId: String = update.callbackQuery.message.photo.maxByOrNull { it.fileSize }!!.fileId
    val groupId: Long = update.callbackQuery.message.chatId
    val messageId: Int = update.callbackQuery.message.messageId
    val voteValue: AutosuggestionVoteValue = AutosuggestionVoteValue.valueOf(update.callbackQuery.data)
    override fun toString(): String {
        return "AutosuggestionVoteUpdate(approver=$approver, messageId=$messageId, voteValue=$voteValue)"
    }
}