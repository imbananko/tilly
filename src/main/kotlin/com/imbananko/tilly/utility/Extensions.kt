package com.imbananko.tilly.utility

import com.imbananko.tilly.model.VoteValue
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember
import org.telegram.telegrambots.meta.api.objects.ChatMember
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User

fun Update.isP2PChat(): Boolean = this.hasMessage() && this.message.chat.isUserChat

fun Update.hasPhoto(): Boolean = this.hasMessage() && this.message.hasPhoto()

fun Update.hasVote(): Boolean =
    this.hasCallbackQuery() && runCatching {
      setOf(*VoteValue.values()).contains(extractVoteValue())
    }.getOrDefault(false)

fun Update.canBeExplanation(): Boolean =
    this.hasMessage() && this.message.replyToMessage != null && this.message.replyToMessage.from.bot && runCatching {
      this.message.replyToMessage.text.endsWith("поясни за мем, на это у тебя есть сутки")
    }.getOrDefault(false)

fun Update.extractVoteValue(): VoteValue =
    VoteValue.valueOf(this.callbackQuery.data.split(" ".toRegex()).dropLastWhile { it.isEmpty() }[0])

fun Update.hasPermissions(execute: (GetChatMember) -> ChatMember, chatIdOpt: Long? = null): Boolean {
  val userId = this.message?.from?.id ?: this.callbackQuery?.from?.id
  val chatId = chatIdOpt ?: this.callbackQuery?.message?.chatId

  return runCatching {
    execute(GetChatMember().apply { this.setChatId(chatId); this.setUserId(userId) }).canSendMessages ?: true
  }.getOrDefault(false)
}

fun User.mention(): String = "[${userName ?: firstName ?: "мутный тип"}](tg://user?id=$id)"