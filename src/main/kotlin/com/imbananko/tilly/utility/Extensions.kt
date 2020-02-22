package com.imbananko.tilly.utility

import com.imbananko.tilly.model.VoteValue
import org.telegram.telegrambots.meta.api.objects.MemberStatus
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User

fun Update.hasMeme() = this.hasMessage() && this.message.chat.isUserChat && this.message.hasPhoto()

fun Update.hasStatsCommand() = this.hasMessage() && this.message.chat.isUserChat && this.message.isCommand

fun Update.hasVote() = this.hasCallbackQuery()
    && (this.callbackQuery.message.isSuperGroupMessage || this.callbackQuery.message.isChannelMessage)
    && runCatching {
  setOf(*VoteValue.values()).map { it.name }.contains(this.callbackQuery.data)
}.getOrDefault(false)

fun User.mention(): String = "[${this.userName ?: this.firstName ?: "мутный тип"}](tg://user?id=${this.id})"

fun String.isChatUserStatus(): Boolean = chatUserStatuses.contains(this)

private val chatUserStatuses = setOf(MemberStatus.ADMINISTRATOR, MemberStatus.CREATOR, MemberStatus.MEMBER)


