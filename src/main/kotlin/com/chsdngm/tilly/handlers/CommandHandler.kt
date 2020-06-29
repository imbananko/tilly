package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.CommandUpdate
import com.chsdngm.tilly.model.CommandUpdate.Command
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.utility.TelegramConfig.Companion.BOT_USERNAME
import com.chsdngm.tilly.utility.TelegramConfig.Companion.api
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage

@Service
class CommandHandler(private val memeRepository: MemeRepository) : AbstractHandler<CommandUpdate> {
  private val log = LoggerFactory.getLogger(javaClass)

  override fun handle(update: CommandUpdate) {
    when (update.value) {
      Command.STATS -> sendStats(update)
      Command.HELP, Command.START -> sendInfoMessage(update)
      else -> log.error("Unknown command from update=$update")
    }
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

    runCatching {
      api.execute(SendMessage()
          .setChatId(update.senderId)
          .setText(message)
      )
    }.onSuccess {
      log.info("Sent stats to user=${update.senderId}")
    }.onFailure {
      log.error("Failed to send stats to user=$update", it)
    }
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

    runCatching {
      api.execute(SendMessage()
          .setChatId(update.senderId)
          .setParseMode(ParseMode.HTML)
          .setText(infoText)
      )
    }.onSuccess {
      log.info("Sent info message to user=${update.senderId}")
    }.onFailure {
      log.error("Failed to send info message to user=$update", it)
    }
  }
}
