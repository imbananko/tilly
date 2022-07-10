package com.chsdngm.tilly.utility

import com.chsdngm.tilly.format
import com.chsdngm.tilly.model.AutosuggestionVoteValue
import com.chsdngm.tilly.model.Meme
import com.chsdngm.tilly.model.PrivateVoteValue
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.utility.TillyConfig.Companion.api
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.*
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import java.time.Instant

fun Update.hasMeme() = this.hasMessage() && this.message.chat.isUserChat && this.message.hasPhoto()

fun Update.hasCommand() = this.hasMessage() &&
        (this.message.chat.isUserChat || this.message.chatId.toString() == TillyConfig.BETA_CHAT_ID) &&
        this.message.isCommand

fun Update.hasVote() = this.hasCallbackQuery()
        && (this.callbackQuery.message.isSuperGroupMessage || this.callbackQuery.message.isChannelMessage)
        && runCatching {
    setOf(*VoteValue.values()).map { it.name }.contains(this.callbackQuery.data)
}.getOrDefault(false)

fun Update.hasPrivateVote() = this.hasCallbackQuery()
        && this.callbackQuery.message.chat.isUserChat
        && runCatching {
    setOf(*PrivateVoteValue.values()).map { it.name }.contains(this.callbackQuery.data)
}.getOrDefault(false)

fun Update.hasAutosuggestionVote() = this.hasCallbackQuery()
        && this.callbackQuery.message.chatId.toString() == TillyConfig.BETA_CHAT_ID
        && runCatching {
    setOf(*AutosuggestionVoteValue.values()).map { it.name }.contains(this.callbackQuery.data)
}.getOrDefault(false)

fun User.mention(): String =
    if (this.id == TillyConfig.BOT_ID) "montorn"
    else """<a href="tg://user?id=${this.id}">${this.userName ?: this.firstName ?: "мутный тип"}</a>"""

fun ChatMember.isFromChat(): Boolean = chatUserStatuses.contains(this.status)

private val chatUserStatuses = setOf(MemberStatus.ADMINISTRATOR, MemberStatus.CREATOR, MemberStatus.MEMBER)

fun createMarkup(stats: Map<VoteValue, Int>) = InlineKeyboardMarkup().apply {
    keyboard = listOf(
        listOf(
            createVoteInlineKeyboardButton(VoteValue.UP, stats.getOrDefault(VoteValue.UP, 0)),
            createVoteInlineKeyboardButton(VoteValue.DOWN, stats.getOrDefault(VoteValue.DOWN, 0))
        )
    )
}

fun updateStatsInSenderChat(meme: Meme) {
    if (meme.privateReplyMessageId != null) {
        val caption = meme.status.description +
                meme.votes.groupingBy { it.value }.eachCount().entries.sortedBy { it.key }
                    .joinToString(prefix = " статистика: \n\n", transform = { (value, sum) -> "${value.emoji}: $sum" })

        EditMessageText().apply {
            chatId = meme.senderId.toString()
            messageId = meme.privateReplyMessageId
            text = caption
        }.let { api.execute(it) }
    }
}

fun updateStatsInSenderChat(meme: com.chsdngm.tilly.exposed.Meme) {
    if (meme.privateReplyMessageId != null) {
        val caption = meme.status.description +
                meme.votes.groupingBy { it.value }.eachCount().entries.sortedBy { it.key }
                    .joinToString(
                        prefix = " статистика: \n\n",
                        transform = { (value, sum) -> "${value.emoji}: $sum" })

        EditMessageText().apply {
            chatId = meme.senderId.toString()
            messageId = meme.privateReplyMessageId
            text = caption
        }.let { api.execute(it) }
    }
}

fun logExceptionInBetaChat(ex: Throwable): Message =
    SendMessage().apply {
        chatId = TillyConfig.BETA_CHAT_ID
        text = ex.format(update = null)
        parseMode = ParseMode.HTML
    }.let { method -> api.execute(method) }

private fun createVoteInlineKeyboardButton(voteValue: VoteValue, voteCount: Int) =
    InlineKeyboardButton().also {
        it.text = if (voteCount == 0) voteValue.emoji else voteValue.emoji + " " + voteCount
        it.callbackData = voteValue.name
    }

fun Instant.minusDays(days: Int): Instant = this.minusSeconds(days.toLong() * 24 * 60 * 60)
