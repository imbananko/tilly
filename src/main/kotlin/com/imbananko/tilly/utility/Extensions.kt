package com.imbananko.tilly.utility

import com.imbananko.tilly.model.VoteValue
import org.telegram.telegrambots.meta.api.objects.MemberStatus
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

fun Update.hasMeme() = this.hasMessage() && this.message.chat.isUserChat && this.message.hasPhoto()

fun Update.hasStatsCommand() = this.hasMessage() && this.message.chat.isUserChat && this.message.isCommand

fun Update.hasVote() = this.hasCallbackQuery()
    && (this.callbackQuery.message.isGroupMessage || this.callbackQuery.message.isSuperGroupMessage)
    && runCatching {
  setOf(*VoteValue.values()).contains(extractVoteValue())
}.getOrDefault(false)

fun Update.extractVoteValue() =
    VoteValue.valueOf(this.callbackQuery.data.split(" ".toRegex()).dropLastWhile { it.isEmpty() }[0])

fun User.mention(): String = "[${this.userName ?: this.firstName ?: "мутный тип"}](tg://user?id=${this.id})"

fun String.isChatUserStatus(): Boolean = chatUserStatuses.contains(this)

private val chatUserStatuses = setOf(MemberStatus.ADMINISTRATOR, MemberStatus.CREATOR, MemberStatus.MEMBER)

fun createMarkup(stats: Map<VoteValue, Int>): InlineKeyboardMarkup {
  fun createVoteInlineKeyboardButton(voteValue: VoteValue, voteCount: Int): InlineKeyboardButton {
    return InlineKeyboardButton().also {
      it.text = if (voteCount == 0) voteValue.emoji else voteValue.emoji + " " + voteCount
      it.callbackData = voteValue.name
    }
  }

  return InlineKeyboardMarkup().setKeyboard(
      listOf(
          listOf(
              createVoteInlineKeyboardButton(VoteValue.UP, stats.getOrDefault(VoteValue.UP, 0)),
              createVoteInlineKeyboardButton(VoteValue.DOWN, stats.getOrDefault(VoteValue.DOWN, 0))
          )
      )
  )
}
