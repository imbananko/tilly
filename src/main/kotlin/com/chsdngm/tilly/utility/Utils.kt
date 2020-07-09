package com.chsdngm.tilly.utility

import com.chsdngm.tilly.model.PrivateVoteValue
import com.chsdngm.tilly.model.VoteValue
import org.telegram.telegrambots.meta.api.methods.ForwardMessage
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.ChatMember
import org.telegram.telegrambots.meta.api.objects.MemberStatus
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

fun Update.hasMeme() = this.hasMessage() && this.message.chat.isUserChat && this.message.hasPhoto()

fun Update.hasStatsCommand() = this.hasMessage() && this.message.chat.isUserChat && this.message.isCommand

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

fun User.mention(): String = """<a href="tg://user?id=${this.id}">${this.userName ?: this.firstName ?: "мутный тип"}</a>"""

fun ChatMember.isFromChat(): Boolean = chatUserStatuses.contains(this.status)

private val chatUserStatuses = setOf(MemberStatus.ADMINISTRATOR, MemberStatus.CREATOR, MemberStatus.MEMBER)

fun ForwardMessage.setChatId(chatId: Int): ForwardMessage = this.setChatId(chatId.toLong())

fun hasLocalTag(caption: String?) = caption?.contains("#local") ?: false

fun SendMessage.setChatId(chatId: Int): SendMessage = this.setChatId(chatId.toLong())

fun createMarkup(stats: Map<VoteValue, Int>): InlineKeyboardMarkup = InlineKeyboardMarkup().setKeyboard(
    listOf(
        listOf(
            createVoteInlineKeyboardButton(VoteValue.UP, stats.getOrDefault(VoteValue.UP, 0)),
            createVoteInlineKeyboardButton(VoteValue.DOWN, stats.getOrDefault(VoteValue.DOWN, 0))
        )))

private fun createVoteInlineKeyboardButton(voteValue: VoteValue, voteCount: Int) =
    InlineKeyboardButton().also {
      it.text = if (voteCount == 0) voteValue.emoji else voteValue.emoji + " " + voteCount
      it.callbackData = voteValue.name
    }