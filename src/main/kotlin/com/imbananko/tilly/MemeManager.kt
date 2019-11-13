package com.imbananko.tilly

import com.imbananko.tilly.model.MemeEntity
import com.imbananko.tilly.model.VoteEntity
import com.imbananko.tilly.model.VoteValue
import com.imbananko.tilly.model.VoteValue.DOWN
import com.imbananko.tilly.model.VoteValue.EXPLAIN
import com.imbananko.tilly.model.VoteValue.UP
import com.imbananko.tilly.repository.MemeRepository
import com.imbananko.tilly.repository.VoteRepository
import com.imbananko.tilly.similarity.MemeMatcher
import com.imbananko.tilly.utility.extractVoteValue
import com.imbananko.tilly.utility.hasPhoto
import com.imbananko.tilly.utility.hasVote
import com.imbananko.tilly.utility.isP2PChat
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
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
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
          this.setUserId(memeOfTheWeek.senderId)
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

          val statistics = voteRepository.getStats(memeOfTheWeek.chatId, memeOfTheWeek.messageId)
          val fallbackMemeOfTheWeekMessage = execute(
              SendPhoto()
                  .setChatId(chatId)
                  .setPhoto(memeOfTheWeek.fileId)
                  .setParseMode(ParseMode.MARKDOWN)
                  .setCaption(congratulationText)
                  .setReplyMarkup(createMarkup(statistics, statistics.getOrDefault(EXPLAIN, 0) >= 3))
          )

          memeRepository.migrateMeme(memeOfTheWeek.chatId, memeOfTheWeek.messageId, fallbackMemeOfTheWeekMessage.messageId)
          voteRepository.migrateVotes(memeOfTheWeek.chatId, memeOfTheWeek.messageId, fallbackMemeOfTheWeekMessage.messageId)

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

  override fun getBotToken(): String? = token

  override fun getBotUsername(): String? = username

  override fun onUpdateReceived(update: Update) {
    if (update.isP2PChat() && update.hasPhoto()) processMeme(update)
    if (update.hasVote()) processVote(update)
  }


  private fun processMeme(update: Update) {
    val message = update.message
    val fileId = message.photo[0].fileId
    val mention = message.from.mention()

    val memeCaption = (message.caption?.trim()?.run { this + "\n\n" } ?: "") + "Sender: " + mention

    val processMemeIfUnique = {
      runCatching {
        execute(
            SendPhoto()
                .setChatId(chatId)
                .setPhoto(fileId)
                .setParseMode(ParseMode.MARKDOWN)
                .setCaption(memeCaption)
                .setReplyMarkup(createMarkup(emptyMap(), false)))
      }.onSuccess { sentMemeMessage ->
        val meme = MemeEntity(sentMemeMessage.chatId, sentMemeMessage.messageId, message.from.id, message.photo[0].fileId)
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
                .setChatId(chatId)
                .setPhoto(fileId)
                .setParseMode(ParseMode.MARKDOWN)
                .setCaption("$mention попытался отправить этот мем, не смотря на то, что его уже скидывали выше. Позор...")
                .setReplyToMessageId(memeRepository.messageIdByFileId(existingMemeId, chatId))
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
    val messageId = message.messageId
    val vote = update.extractVoteValue()
    val voteSender = update.callbackQuery.from

    val wasExplained = message
        .replyMarkup
        .keyboard[0][1]
        .callbackData
        .contains("EXPLAINED")

    val voteEntity = VoteEntity(targetChatId, messageId, voteSender.id, vote)

    val memeSenderId = memeRepository.getMemeSender(targetChatId, messageId);
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

      val memeSenderFromCaption = message.caption.split("Sender: ".toRegex()).dropLastWhile { it.isEmpty() }[1]
      val replyText = "[$memeSenderFromCaption](tg://user?id=$memeSenderId), поясни за мем"

      runCatching {
        execute<Message, SendMessage>(
            SendMessage()
                .setChatId(targetChatId)
                .setReplyToMessageId(update.callbackQuery.message.messageId)
                .setText(replyText)
                .setParseMode(ParseMode.MARKDOWN)
        )
      }
          .onSuccess { log.info("Successful reply for explaining") }
          .onFailure { throwable -> log.error("Failed to reply for explaining. Exception=" + throwable.message) }
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
}
