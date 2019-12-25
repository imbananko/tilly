package com.imbananko.tilly.utility

import com.imbananko.tilly.model.VoteValue
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import java.time.Instant

fun Update.hasMeme() = this.hasMessage() && this.message.chat.isUserChat && this.message.hasPhoto()

fun Update.hasStatsCommand() = this.hasMessage() && this.message.chat.isUserChat && this.message.isCommand && this.message.text == "/stats"

fun Update.hasVote() = this.hasCallbackQuery() && runCatching {
      setOf(*VoteValue.values()).contains(extractVoteValue())
    }.getOrDefault(false)

fun Update.extractVoteValue() =
    VoteValue.valueOf(this.callbackQuery.data.split(" ".toRegex()).dropLastWhile { it.isEmpty() }[0])

fun User.mention(): String = "[${this.userName ?: this.firstName ?: "мутный тип"}](tg://user?id=${this.id})"

fun Message.print(): String =
    "Message(messageId=${this.messageId},chatId=${this.chatId},userId=${this.from?.id},userName=${this.from?.userName})"

fun Message.isOld(): Boolean = Instant.ofEpochSecond(this.date.toLong()) < Instant.now().minusSeconds(60 * 60 * 24 * 7)
