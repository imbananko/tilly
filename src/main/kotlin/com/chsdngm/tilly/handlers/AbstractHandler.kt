package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.VoteValue
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

abstract class AbstractHandler<T> {
  abstract fun handle(update: T)

  companion object {

    private fun createVoteInlineKeyboardButton(voteValue: VoteValue, voteCount: Int) =
        InlineKeyboardButton().also {
          it.text = if (voteCount == 0) voteValue.emoji else voteValue.emoji + " " + voteCount
          it.callbackData = voteValue.name
        }

    fun createMarkup(stats: Map<VoteValue, Int>): InlineKeyboardMarkup = InlineKeyboardMarkup().setKeyboard(
        listOf(
            listOf(
                createVoteInlineKeyboardButton(VoteValue.UP, stats.getOrDefault(VoteValue.UP, 0)),
                createVoteInlineKeyboardButton(VoteValue.DOWN, stats.getOrDefault(VoteValue.DOWN, 0))
            )))
  }
}
