package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.CommandUpdate
import com.chsdngm.tilly.model.CommandUpdate.Command
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.utility.TillyConfig
import com.chsdngm.tilly.utility.TillyConfig.Companion.BOT_USERNAME
import com.chsdngm.tilly.utility.TillyConfig.Companion.api
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

@Service
class CommandHandler(private val memeRepository: MemeRepository) : AbstractHandler<CommandUpdate> {
  private val log = LoggerFactory.getLogger(javaClass)

  override fun handle(update: CommandUpdate) {
    when (update.value) {
      Command.STATS -> sendStats(update)
      Command.HELP, Command.START -> sendInfoMessage(update)
      Command.DONATE -> sendDonationMarkup(update)
      Command.CONFIG -> changeConfig(update)
      else -> log.warn("unknown command from update=$update")
    }
    log.info("processed command update=$update")
  }

  private fun sendStats(update: CommandUpdate) {
    val userMemes = memeRepository.findBySenderId(update.senderId.toInt())

    val likes = userMemes.flatMap { it.votes }.map { it.value }.filter { it == VoteValue.UP }.size
    val dislikes = userMemes.flatMap { it.votes }.map { it.value }.filter { it == VoteValue.DOWN }.size
    val published = userMemes.filter { it.channelMessageId != null }.size

    val message =
        if (userMemes.isEmpty())
          "You have no statistics yet!"
        else
          """
          Your statistics:

          Memes sent: ${userMemes.size} (on channel: $published)
          ${VoteValue.UP.emoji}: $likes
          ${VoteValue.DOWN.emoji}: $dislikes
        """.trimIndent()

      api.execute(SendMessage()
          .setChatId(update.senderId)
          .setText(message)
      )
  }

  fun sendInfoMessage(update: CommandUpdate) {
    val infoText = """
      Привет, я $BOT_USERNAME. 
      
      Чат со мной - это место для твоих лучших мемов, которыми охота поделиться.
      Сейчас же отправляй мне самый крутой мем, и, если он пройдёт модерацию, то попадёт на канал <a href="https://t.me/chsdngm/">че с деньгами</a>. 
      Мем, набравший за неделю больше всех кристаллов, станет <b>мемом недели</b>, а его обладатель получит бесконечный респект и поздравление на канале.

      Термины и определения:
      
      Мем (англ. meme) — единица культурной информации, имеющей развлекательный характер (в нашем случае - картинка). 
      Модерация (от лат. moderor — умеряю, сдерживаю) — все мемы проходят предварительную оценку экспертов, а на канал попадут только лучшие. 
      
      За динамикой оценки также можно следить тут.
    """.trimIndent()

    api.execute(SendMessage()
        .setChatId(update.senderId)
        .setParseMode(ParseMode.HTML)
        .setText(infoText)
    )
  }

  fun sendDonationMarkup(update: CommandUpdate) {
    val donationMarkup = InlineKeyboardMarkup(listOf(100, 250, 500).map {
      listOf(InlineKeyboardButton("100").apply {
        text = "$it rub."
        callbackData = text
        url = "${TillyConfig.PAYMENT_URL}?amount=${it * 100}"
      })
    })

    SendMessage()
        .setChatId(update.senderId)
        .setParseMode(ParseMode.HTML)
        .setReplyMarkup(donationMarkup)
        .setText("Select donation sum: ")
        .let { api.execute(it) }
  }

  fun changeConfig(update: CommandUpdate) {
    val message = when {
      update.text.contains("enable publishing") -> {
        TillyConfig.publishEnabled = true
        "Публикация мемов включена"
      }
      update.text.contains("disable publishing") -> {
        TillyConfig.publishEnabled = false
        "Публикация мемов выключена"
      }
      else ->
        """
          Не удается прочитать сообщение. Правильные команды выглядят так:
          /config enable publishing
          /config disable publishing
        """.trimIndent()
    }

    SendMessage()
      .setChatId(TillyConfig.BETA_CHAT_ID)
      .setParseMode(ParseMode.HTML)
      .setReplyToMessageId(update.messageId)
      .setText(message)
      .let { api.execute(it) }
  }
}
