package com.imbananko.tilly.utility

import com.imbananko.tilly.model.VoteValue
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User

fun Update.isP2PChat() = this.hasMessage() && this.message.chat.isUserChat

fun Update.hasPhoto() = this.hasMessage() && this.message.hasPhoto()

fun Update.hasVote() =
    this.hasCallbackQuery() && runCatching {
      setOf(*VoteValue.values()).contains(extractVoteValue())
    }.getOrDefault(false)

fun Update.extractVoteValue() =
    VoteValue.valueOf(this.callbackQuery.data.split(" ".toRegex()).dropLastWhile { it.isEmpty() }[0])

fun User.mention(): String =
    "[${this.userName ?: this.firstName ?: "мутный тип"}](tg://user?id=${this.id})"