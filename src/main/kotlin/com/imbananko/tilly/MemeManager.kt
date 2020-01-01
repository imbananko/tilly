package com.imbananko.tilly

import com.imbananko.tilly.model.MemeEntity
import com.imbananko.tilly.model.MemeStatsEntry
import com.imbananko.tilly.model.VoteEntity
import com.imbananko.tilly.model.VoteValue
import com.imbananko.tilly.model.VoteValue.*
import com.imbananko.tilly.repository.MemeRepository
import com.imbananko.tilly.repository.VoteRepository
import com.imbananko.tilly.similarity.MemeMatcher
import com.imbananko.tilly.utility.*
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import javax.annotation.PostConstruct

@EnableScheduling
@Component
class MemeManager(private val memeRepository: MemeRepository, private val voteRepository: VoteRepository, private val memeMatcher: MemeMatcher) : TelegramLongPollingBot() {

  private val log = LoggerFactory.getLogger(javaClass)

  @Value("\${target.chat.id}")
  private val chatId: Long = 0

  @Value("\${bot.token}")
  private lateinit var token: String

  @Value("\${bot.username}")
  private lateinit var username: String

  override fun getBotToken(): String? = token

  override fun getBotUsername(): String? = username

  @PostConstruct
  private fun init() {
    memeRepository
        .load(chatId)
        .parallelStream()
        .forEach { me ->
          try {
            memeMatcher.addMeme(me.fileId, downloadFromFileId(me.fileId))
          } catch (e: TelegramApiException) {
            log.error("Failed to load file {}: {}, skipping...", me.fileId, e.message)
          } catch (e: IOException) {
            log.error("Failed to load file {}: {}, skipping...", me.fileId, e.message)
          }
        }
  }

  @Scheduled(cron = "0 0 19 * * WED")
  private fun sendMemeOfTheWeek() {
    runCatching {
      val memeOfTheWeek: MemeEntity? = memeRepository.findMemeOfTheWeek(chatId)

      if (memeOfTheWeek != null) {
        val winner = execute(GetChatMember().apply {
          this.setChatId(memeOfTheWeek.chatId)
          this.userId = memeOfTheWeek.senderId
        }).user
        val congratulationText = "Поздравляем ${winner.mention()} с мемом недели!"
        val memeOfTheWeekMessage = runCatching {
          execute(
              SendMessage()
                  .setChatId(chatId)
                  .setParseMode(ParseMode.MARKDOWN)
                  .setReplyToMessageId(memeOfTheWeek.messageId)
                  .setText(congratulationText)
          )
        }.getOrElse { error ->
          log.error("Can't reply to meme of the week because of", error)
          log.info("Send meme of the week as new message")

          val statistics = voteRepository.getVotes(memeOfTheWeek)
              .groupingBy { vote -> vote.voteValue }
              .eachCount()
          val fallbackMemeOfTheWeekMessage = execute(
              SendPhoto()
                  .setChatId(chatId)
                  .setPhoto(memeOfTheWeek.fileId)
                  .setParseMode(ParseMode.MARKDOWN)
                  .setCaption(congratulationText)
                  .setReplyMarkup(createMarkup(statistics))
          )

          memeRepository.migrateMeme(memeOfTheWeek.chatId, memeOfTheWeek.messageId, fallbackMemeOfTheWeekMessage.messageId)

          fallbackMemeOfTheWeekMessage
        }
        execute(PinChatMessage(memeOfTheWeekMessage.chatId, memeOfTheWeekMessage.messageId))
      } else {
        execute(
            SendMessage()
                .setChatId(chatId)
                .setText("К сожалению, мемов на этой неделе не было...")
        )
      }
    }
        .onSuccess { log.info("Successful send meme of the week") }
        .onFailure { throwable -> log.error("Can't send meme of the week because of", throwable) }
  }

  @Scheduled(cron = "0 0 20 31 12 *")
  private fun sendMemesOfTheYear() {
    fun formatMemeTheYearCaption(meme: MemeEntity): String {
      val userMention = execute(GetChatMember()
          .setChatId(meme.chatId)
          .setUserId(meme.senderId))
          .user.mention()
      val votes = voteRepository.getVotes(meme)
          .groupingBy { vote -> vote.voteValue }
          .eachCount()
          .map { entry -> entry.key.emoji + " " + entry.value }
          .joinToString(prefix = "(", postfix = ")", separator = ", ")
      return "$userMention $votes"
    }

    runCatching {
      execute(SendMessage(chatId, "Топ мемсы прошедшего года:"))
      execute(
          SendMediaGroup(chatId, memeRepository.findMemesOfTheYear(chatId).map { meme ->
            InputMediaPhoto(meme.fileId, formatMemeTheYearCaption(meme))
                .setParseMode(ParseMode.MARKDOWN)
          })
      )
    }
        .onSuccess { log.info("Successful send memes of the year") }
        .onFailure { throwable -> log.error("Can't send memes of the year because of", throwable) }
  }

  override fun onUpdateReceived(update: Update) {
    if (update.hasMeme()) processMeme(update)
    if (update.hasVote()) processVote(update)
    if (update.hasStatsCommand()) sendStats(update)
  }

  private fun sendStats(update: Update) {
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
      execute(
          SendMessage()
              .setChatId(update.message.chatId)
              .setText(formatStatsMessage(voteRepository.getStatsByUser(chatId, update.message.from.id)))
      )
    }.onSuccess {
      log.debug("Sent stats to user=${update.message.from.id}")
    }.onFailure { throwable ->
      log.error("Failed to send stats to user=${update.message.from.id}. Exception=", throwable)
    }
  }

  private fun processMeme(update: Update) {
    val message = update.message
    val fileId = message.photo[0].fileId
    val mention = message.from.mention()

    val memeCaption = message.caption?.trim()

    val processMemeIfUnique = {
      runCatching {
        execute(
            SendPhoto()
                .setChatId(chatId)
                .setPhoto(fileId)
                .setParseMode(ParseMode.MARKDOWN)
                .setCaption(memeCaption)
                .setReplyMarkup(createMarkup(emptyMap())))
      }.onSuccess { sentMemeMessage ->
        val meme = MemeEntity(sentMemeMessage.chatId, sentMemeMessage.messageId, message.from.id, message.photo[0].fileId, false)
        memeRepository.save(meme).also { log.info("Sent meme=$meme") }
      }.onFailure { throwable ->
        log.error("Failed to send meme from message=${message.print()}. Exception=", throwable)
      }
    }

    fun blameWithoutReply(): SendPhoto =
        SendPhoto()
            .setChatId(chatId)
            .setPhoto(fileId)
            .setParseMode(ParseMode.MARKDOWN)
            .setCaption("$mention попытался отправить этот мем, не смотря на то, что его уже скидывали выше. Позор...")

    fun blameWithReply(messageId: Int): SendPhoto =
        blameWithoutReply().setReplyToMessageId(messageId)

    val processMemeIfExists = { existingMemeId: String ->
      runCatching {
        execute(memeRepository.messageIdByFileId(existingMemeId, chatId)?.run {
          blameWithReply(this)
        } ?: blameWithoutReply()
        )
      }.onFailure { ex ->
        if (ex is TelegramApiRequestException) {
          log.warn("Failed to reply with existing meme from message=${message.print()}. Sending message without reply.")
          execute(blameWithoutReply())
        } else {
          throw ex
        }
      }
    }

    runCatching { downloadFromFileId(fileId) }
        .mapCatching { memeFile -> memeMatcher.checkMemeExists(fileId, memeFile) }
        .onSuccess { memeId ->
          if (memeId != null) processMemeIfExists(memeId)
          else processMemeIfUnique()
        }.onFailure { err ->
          log.error("Failed to process meme. Exception=", err)
        }
  }

  private fun processVote(update: Update) {
    val targetChatId = update.callbackQuery.message.chatId
    val messageId = update.callbackQuery.message.messageId

    val vote = VoteEntity(targetChatId, messageId, update.callbackQuery.from.id, update.extractVoteValue())
    val meme = memeRepository.findMeme(targetChatId, messageId) ?: return

//    if (update.callbackQuery.message.isOld() || meme.senderId == vote.voterId) return

    val votes = voteRepository.getVotes(meme)
        .associate { Pair(it.voterId, it.voteValue) }
        .toMutableMap().also {
          it.merge(vote.voterId, vote.voteValue) { old, new -> if (old == new) null else new }
        }

    val shouldRequestExplanation = !meme.explanationRequested && votes.values.filter { vote.voteValue == EXPLAIN }.size >= 3

    runCatching {
      execute(
          EditMessageReplyMarkup()
              .setChatId(targetChatId)
              .setMessageId(messageId)
              .setInlineMessageId(update.callbackQuery.inlineMessageId)
              .setReplyMarkup(createMarkup(votes.values.groupingBy { it }.eachCount()))
      )

      if (shouldRequestExplanation)
        execute<Message, SendMessage>(
            SendMessage()
                .setChatId(targetChatId)
                .setReplyToMessageId(update.callbackQuery.message.messageId)
                .setText("Непонятно, помогите плез!")
                .setParseMode(ParseMode.MARKDOWN)
        )
    }
        .onSuccess {
          if (votes.containsKey(vote.voterId)) voteRepository.insertOrUpdate(vote) else voteRepository.delete(vote)
          if (shouldRequestExplanation) memeRepository.markAsAsked(meme)
          log.info("Processed vote=$vote")
        }
        .onFailure { throwable -> log.error("Failed to process vote=" + vote + ". Exception=" + throwable.message) }
  }

  private fun createMarkup(stats: Map<VoteValue, Int>): InlineKeyboardMarkup {
    fun createVoteInlineKeyboardButton(voteValue: VoteValue, voteCount: Int): InlineKeyboardButton {
      return InlineKeyboardButton().also {
        it.text = if (voteCount == 0) voteValue.emoji else voteValue.emoji + " " + voteCount
        it.callbackData = voteValue.name
      }
    }

    return InlineKeyboardMarkup().setKeyboard(
        listOf(
            listOf(
                createVoteInlineKeyboardButton(UP, stats.getOrDefault(UP, 0)),
                createVoteInlineKeyboardButton(EXPLAIN, stats.getOrDefault(EXPLAIN, 0)),
                createVoteInlineKeyboardButton(DOWN, stats.getOrDefault(DOWN, 0))
            )
        )
    )
  }

  private fun downloadFromFileId(fileId: String): File {
    val telegramFile = execute(GetFile().apply { this.fileId = fileId })
    val tempFile = File.createTempFile("telegram-photo-", "").apply { this.deleteOnExit() }

    FileOutputStream(tempFile)
        .use { out ->
          URL(telegramFile.getFileUrl(botToken)).openStream().use { IOUtils.copy(it, out) }
        }

    return tempFile
  }
}
