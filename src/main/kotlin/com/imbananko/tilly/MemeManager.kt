package com.imbananko.tilly

import com.imbananko.tilly.model.ExplanationEntity
import com.imbananko.tilly.model.MemeEntity
import com.imbananko.tilly.model.VoteEntity
import com.imbananko.tilly.model.VoteValue
import com.imbananko.tilly.model.VoteValue.DOWN
import com.imbananko.tilly.model.VoteValue.EXPLAIN
import com.imbananko.tilly.model.VoteValue.UP
import com.imbananko.tilly.repository.ExplanationRepository
import com.imbananko.tilly.repository.MemeRepository
import com.imbananko.tilly.repository.VoteRepository
import com.imbananko.tilly.similarity.MemeMatcher
import com.imbananko.tilly.utility.extractVoteValue
import com.imbananko.tilly.utility.hasPhoto
import com.imbananko.tilly.utility.hasVote
import com.imbananko.tilly.utility.isP2PChat
import com.imbananko.tilly.utility.canBeExplanation
import com.imbananko.tilly.utility.mention
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
import org.telegram.telegrambots.meta.api.methods.groupadministration.RestrictChatMember
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.objects.ChatPermissions
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ForceReplyKeyboard
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.annotation.PostConstruct
import kotlin.concurrent.thread

@EnableScheduling
@Component
class MemeManager(private val memeRepository: MemeRepository,
                  private val voteRepository: VoteRepository,
                  private val explanationRepository: ExplanationRepository,
                  private val memeMatcher: MemeMatcher) : TelegramLongPollingBot() {

  private val log = LoggerFactory.getLogger(javaClass)

  @Value("\${target.chat.id}")
  private val chatId: Long = 0

  @Value("\${bot.token}")
  private lateinit var token: String

  @Value("\${bot.username}")
  private lateinit var username: String

  @PostConstruct
  private fun init() {
    memeRepository
        .load(chatId)
        .parallelStream()
        .forEach { me ->
          try {
            memeMatcher.addMeme(me.fileId, downloadFromFileId(me.fileId))
          } catch (e: Exception) {
            log.error("Failed to load file {}: {}, skipping...", me.fileId, e.message)
          }
        }
  }

  @Scheduled(fixedRate = 30 * 60 * 1000)
  private fun listenExpiredExplanations() {
    explanationRepository.listExpiredExplanations()
        .forEach { processExpiredExplanation(it) }
  }

  override fun getBotToken(): String? = token

  override fun getBotUsername(): String? = username

  override fun onUpdateReceived(update: Update) {
    if (update.isP2PChat() && update.hasPhoto()) processMeme(update)
    if (update.hasVote()) processVote(update)
    if (update.canBeExplanation()) processExplanation(update)
  }

  private fun processMeme(update: Update) {
    val message = update.message
    val messageFrom = message.from
    val targetChatId = this.chatId
    val canSendMeme = runCatching {
      execute(GetChatMember().apply { this.setChatId(targetChatId); this.setUserId(messageFrom.id) }).canSendMessages ?: true
    }.getOrDefault(false)

    if (!canSendMeme) {
      execute(
          SendMessage()
              .setChatId(message.chatId)
              .setReplyToMessageId(message.messageId)
              .setText("К сожалению, у тебя нет прав отправлять мемы, поэтому этот мем обработан не будет")
      )

      return
    }

    val fileId = message.photo[0].fileId
    val memeCaption = (message.caption?.trim()?.run { this + "\n\n" } ?: "") + "Sender: " + messageFrom.mention()

    val processMemeIfUnique = {
      runCatching {
        execute(
            SendPhoto()
                .setChatId(targetChatId)
                .setPhoto(fileId)
                .setParseMode(ParseMode.MARKDOWN)
                .setCaption(memeCaption)
                .setReplyMarkup(createMarkup(emptyMap(), false)))
      }.onSuccess { sentMemeMessage ->
        val meme = MemeEntity(sentMemeMessage.chatId, sentMemeMessage.messageId, messageFrom.id, fileId)
        log.info("Sent meme=$meme")
        memeRepository.save(meme)
      }.onFailure { throwable ->
        log.error("Failed to send meme from message=$message. Exception=", throwable)
      }
    }

    val processMemeIfExists = { existingMemeId: String ->
      runCatching {
        execute(
            SendPhoto()
                .setChatId(targetChatId)
                .setPhoto(fileId)
                .setParseMode(ParseMode.MARKDOWN)
                .setCaption("${messageFrom.mention()} попытался отправить этот мем, не смотря на то, что его уже скидывали выше. Позор...")
                .setReplyToMessageId(memeRepository.messageIdByFileId(existingMemeId, targetChatId))
        )
      }.onFailure { throwable: Throwable ->
        log.error("Failed to reply with existing meme from message=$message. Exception=", throwable)
      }
    }

    runCatching { downloadFromFileId(fileId) }
        .mapCatching { memeFile -> memeMatcher.checkMemeExists(fileId, memeFile).getOrThrow()!! }
        .mapCatching { memeId -> processMemeIfExists(memeId).getOrThrow() }
        .onFailure { throwable: Throwable ->
          log.error("Failed to check if meme is unique, sending anyway. Exception=", throwable)
          processMemeIfUnique()
        }
  }

  private fun processVote(update: Update) {
    val message = update.callbackQuery.message
    val targetChatId = message.chatId
    val voteSender = update.callbackQuery.from

    val messageId = message.messageId
    val vote = update.extractVoteValue()

    val wasExplained = message
        .replyMarkup
        .keyboard[0][1]
        .callbackData
        .contains("EXPLAINED")

    val voteEntity = VoteEntity(targetChatId, messageId, voteSender.id, vote)

    val memeSenderId = memeRepository.getMemeSender(targetChatId, messageId)
    if (memeSenderId == voteSender.id) return

    if (voteRepository.exists(voteEntity)) voteRepository.delete(voteEntity)
    else voteRepository.insertOrUpdate(voteEntity)


    val statistics = voteRepository.getStats(targetChatId, messageId)
    val shouldMarkExplained = vote == EXPLAIN && !wasExplained && statistics.getOrDefault(EXPLAIN, 0) == 3

    runCatching {
      execute(
          EditMessageReplyMarkup()
              .setMessageId(messageId)
              .setChatId(targetChatId)
              .setInlineMessageId(update.callbackQuery.inlineMessageId)
              .setReplyMarkup(createMarkup(statistics, wasExplained || shouldMarkExplained))
      )
    }
        .onSuccess { log.info("Processed vote=$voteEntity") }
        .onFailure { throwable -> log.error("Failed to process vote=" + voteEntity + ". Exception=" + throwable.message) }

    if (shouldMarkExplained) {

      val replyText = "[${update.callbackQuery.message.caption.replace("Sender: ", "")}](tg://user?id=$memeSenderId)" +
          ", поясни за мем, на это у тебя есть сутки"

      runCatching {
        execute<Message, SendMessage>(
            SendMessage()
                .setChatId(targetChatId)
                .setReplyToMessageId(update.callbackQuery.message.messageId)
                .setText(replyText)
                .setParseMode(ParseMode.MARKDOWN)
                .setReplyMarkup(ForceReplyKeyboard().apply { this.selective = true })
        )
      }.mapCatching { replyMessage ->
        thread {
          val explainTill = Instant.now().plus(1, ChronoUnit.DAYS)
          val explanation = ExplanationEntity(memeSenderId, targetChatId, messageId, replyMessage.messageId, explainTill)
          explanationRepository.save(explanation)
          log.info("Successful reply for explaining")
        }
      }.onFailure { throwable -> log.error("Failed to reply for explaining. Exception=", throwable) }
    }
  }

  private fun processExplanation(update: Update) {
    thread {
      runCatching {
        val senderId = update.message.from.id
        val chatId = update.message.chatId
        val explainReplyMessageId = update.message.replyToMessage.messageId
        explanationRepository.deleteExplanation(senderId, chatId, explainReplyMessageId)
      }.onFailure { log.error("Can't process explanation because of", it) }
    }
  }

  private fun createMarkup(stats: Map<VoteValue, Int>, markExplained: Boolean): InlineKeyboardMarkup {
    fun createVoteInlineKeyboardButton(voteValue: VoteValue, voteCount: Int): InlineKeyboardButton {
      val callbackData =
          if (voteValue == EXPLAIN && markExplained) voteValue.name + " EXPLAINED"
          else voteValue.name

      return InlineKeyboardButton().also {
        it.text = if (voteCount == 0) voteValue.emoji else voteValue.emoji + " " + voteCount
        it.callbackData = callbackData
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

  private fun processExpiredExplanation(explanation: ExplanationEntity) {
    val banMessage = execute<Message, SendMessage>(
        SendMessage()
            .setChatId(explanation.chatId)
            .setParseMode(ParseMode.MARKDOWN)
            .setReplyToMessageId(explanation.explainReplyMessageId)
            .setText("Время вышло. К сожалению, я вынужден отправить [тебя](tg://user?id=${explanation.userId}) в бан")
    )
    runCatching {
      execute(RestrictChatMember(explanation.chatId, explanation.userId).apply {
        this.setUntilDate(Instant.now().plus(1, ChronoUnit.DAYS))
        this.setPermissions(ChatPermissions())
      })
    }.onFailure { ex ->
      log.error("Can't ban user ${explanation.userId} because of", ex)
      execute(SendMessage()
          .setChatId(explanation.chatId)
          .setParseMode(ParseMode.MARKDOWN)
          .setReplyToMessageId(banMessage.messageId)
          .setText("Забанить не получилось, потому что либо у меня нет прав на это, либо пользователь является админом")
      )
    }
    explanationRepository.deleteExplanation(explanation.userId, explanation.chatId, explanation.explainReplyMessageId)
  }
}
