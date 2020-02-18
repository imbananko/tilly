package com.imbananko.tilly.handlers

import com.imbananko.tilly.model.VoteValue
import org.springframework.beans.factory.annotation.Value
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.meta.ApiContext
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

abstract class AbstractHandler<T : Any> : DefaultAbsSender(ApiContext.getInstance(DefaultBotOptions::class.java)) {
  @Value("\${target.chat.id}")
  protected val chatId: Long = 0

  @Value("\${target.channel.id}")
  protected val channelId: Long = 0

  @Value("\${bot.token}")
  protected lateinit var token: String

  override fun getBotToken(): String = token

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
