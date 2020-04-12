package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.utility.BotConfig
import org.jsoup.Jsoup.connect
import org.jsoup.nodes.TextNode
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.meta.ApiContext
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

abstract class AbstractHandler<T> : DefaultAbsSender(ApiContext.getInstance(DefaultBotOptions::class.java)), BotConfig {
  abstract fun handle(update: T)

  companion object {
    private const val domainUrl = "http://chsdngm.com"
    private const val commentsUrl = "$domainUrl/comments"

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

    fun createMarkup(stats: Map<VoteValue, Int>, identifier: Int): InlineKeyboardMarkup = InlineKeyboardMarkup().setKeyboard(
        listOf(
            listOf(
                createVoteInlineKeyboardButton(VoteValue.UP, stats.getOrDefault(VoteValue.UP, 0)),
                createVoteInlineKeyboardButton(VoteValue.DOWN, stats.getOrDefault(VoteValue.DOWN, 0))
            ),
            listOf(InlineKeyboardButton(getCommentsButtonText(identifier)).setUrl("$commentsUrl/$identifier"))
        ))

    private fun getCommentsButtonText(identifier: Int) =
        connect("https://comments.app/embed/view?website=HzZubwqu&page_url=$commentsUrl/$identifier&origin=$domainUrl")
            .get()
            .select("h3.bc-header")
            .first()
            .childNodes()
            .first()
            .let { it as TextNode }
            .text()

  }
}
