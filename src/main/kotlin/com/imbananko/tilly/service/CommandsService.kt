package com.imbananko.tilly.service;

import com.imbananko.tilly.model.MemeStatsEntry
import com.imbananko.tilly.repository.VoteRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update

interface CommandsService {
  fun sendStats(update: Update)
}

@Component
class CommandsServiceImpl(val voteRepository: VoteRepository,
                          val sendMessage: Function1<SendMessage, Message>) : CommandsService {

  @Value("\${target.channel.id}")
  private val channelId: Long = 0

  override fun sendStats(update: Update) {
    fun formatStatsMessage(stats: List<MemeStatsEntry>): String =
        if (stats.isEmpty())
          "You have no statistics yet!"
        else
          """
          Your statistics:
          
          Memes sent: ${stats.size}
        """.trimIndent() +
              stats
                  .flatMap { it.counts.asIterable() }
                  .groupBy({ it.first }, { it.second })
                  .mapValues { it.value.sum() }
                  .toList()
                  .sortedBy { it.first }
                  .joinToString("\n", prefix = "\n\n", transform = { (value, sum) -> "${value.emoji}: $sum" })

    runCatching {
      sendMessage(
          SendMessage()
              .setChatId(update.message.chatId)
              .setText(formatStatsMessage(voteRepository.getStatsByUser(channelId, update.message.from.id)))
      )
    }.onSuccess {
      log.debug("Sent stats to user=${update.message.from.id}")
    }.onFailure { throwable ->
      log.error("Failed to send stats to user=${update.message.from.id}. Exception=", throwable)
    }
  }

  private val log = LoggerFactory.getLogger(javaClass)
}
