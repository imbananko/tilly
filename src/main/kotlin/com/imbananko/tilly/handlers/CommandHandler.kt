package com.imbananko.tilly.handlers

import com.imbananko.tilly.model.Command
import com.imbananko.tilly.model.CommandUpdate
import com.imbananko.tilly.model.MemeStatsEntry
import com.imbananko.tilly.repository.VoteRepository
import com.imbananko.tilly.utility.BotConfig
import com.imbananko.tilly.utility.BotConfigImpl
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage

@Component
class CommandHandler(private val voteRepository: VoteRepository,
                     private val botConfig: BotConfigImpl) : AbstractHandler<CommandUpdate>(), BotConfig by botConfig {
  private val log = LoggerFactory.getLogger(javaClass)

  override fun handle(update: CommandUpdate) {
    when (update.value) {
      Command.STATS -> sendStats(update)
      else -> log.error("Unknown command from update=$update")
    }
  }

  private fun sendStats(update: CommandUpdate) =
      runCatching {
        execute(SendMessage()
            .setChatId(update.senderId)
            .setText(formatStatsMessage(voteRepository.getStatsByUser(channelId, update.senderId.toInt())))
        )
      }.onSuccess {
        log.debug("Sent stats to user=${update.senderId}")
      }.onFailure {
        log.error("Failed to send stats to user=$update", it)
      }


  private fun formatStatsMessage(stats: List<MemeStatsEntry>): String =
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
}
