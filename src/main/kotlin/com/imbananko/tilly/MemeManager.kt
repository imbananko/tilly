package com.imbananko.tilly

import com.imbananko.tilly.model.MemeEntity
import com.imbananko.tilly.model.VoteEntity
import com.imbananko.tilly.model.VoteValue
import com.imbananko.tilly.model.VoteValue.DOWN
import com.imbananko.tilly.model.VoteValue.UP
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
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.objects.Update
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
class MemeManager(private val memeRepository: MemeRepository,
                  private val voteRepository: VoteRepository,
                  private val memeMatcher: MemeMatcher)
  : TelegramLongPollingBot() {

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
      val memeOfTheWeek: MemeEntity = memeRepository.findMemeOfTheWeek(chatId)!!

      run {
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

          val statistics = voteRepository.getStats(memeOfTheWeek.chatId, memeOfTheWeek.messageId)
          val fallbackMemeOfTheWeekMessage = execute(
              SendPhoto()
                  .setChatId(chatId)
                  .setPhoto(memeOfTheWeek.fileId)
                  .setParseMode(ParseMode.MARKDOWN)
                  .setCaption(congratulationText)
                  .setReplyMarkup(createMarkup(statistics))
          )

          memeRepository.migrateMeme(memeOfTheWeek.chatId, memeOfTheWeek.messageId, fallbackMemeOfTheWeekMessage.messageId)
          voteRepository.migrateVotes(memeOfTheWeek.chatId, memeOfTheWeek.messageId, fallbackMemeOfTheWeekMessage.messageId)

          fallbackMemeOfTheWeekMessage
        }
        execute(PinChatMessage(memeOfTheWeekMessage.chatId, memeOfTheWeekMessage.messageId))
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
                .setReplyMarkup(createMarkup(emptyMap())))
      }.onSuccess { sentMemeMessage ->
        val meme = MemeEntity(sentMemeMessage.chatId, sentMemeMessage.messageId, message.from.id, message.photo[0].fileId)
        memeRepository.save(meme)

        log.info("Sent meme=$meme")
      }.onFailure { throwable ->
        if (throwable is TelegramApiRequestException) {
          log.error("Failed to send meme from message=$message. Exception=${throwable.apiResponse}")
        } else {
          log.error("Failed to send meme from message=$message. Exception=${throwable.message}")
        }
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
        log.error("Failed to reply with existing meme from message=$message. Exception=${throwable.cause}")
      }
    }

    runCatching { downloadFromFileId(fileId) }
        .mapCatching { memeFile -> memeMatcher.checkMemeExists(fileId, memeFile).getOrThrow()!! }
        .mapCatching { memeId -> processMemeIfExists(memeId).getOrThrow() }
        .onFailure { throwable: Throwable ->
          log.error("Failed to check if meme is unique, sending anyway. Exception=${throwable.cause}")
          processMemeIfUnique()
        }
  }

  private fun processVote(update: Update) {
    val targetChatId = update.callbackQuery.message.chatId
    val messageId = update.callbackQuery.message.messageId
    val voteSender = update.callbackQuery.from
    if (memeRepository.getMemeSender(targetChatId, messageId) == voteSender.id) return

    val voteEntity = VoteEntity(targetChatId, messageId, voteSender.id, update.extractVoteValue())

    if (voteRepository.exists(voteEntity)) voteRepository.delete(voteEntity) else voteRepository.insertOrUpdate(voteEntity)

    runCatching {
      execute(
          EditMessageReplyMarkup()
              .setMessageId(messageId)
              .setChatId(targetChatId)
              .setInlineMessageId(update.callbackQuery.inlineMessageId)
              .setReplyMarkup(createMarkup(voteRepository.getStats(targetChatId, messageId)))
      )
    }
        .onSuccess { log.info("Processed vote=$voteEntity") }
        .onFailure { throwable -> log.error("Failed to process vote=" + voteEntity + ". Exception=" + throwable.cause) }
  }

  private fun createMarkup(stats: Map<VoteValue, Int>): InlineKeyboardMarkup {
    fun createVoteInlineKeyboardButton(voteValue: VoteValue, voteCount: Int): InlineKeyboardButton {
      return InlineKeyboardButton(if (voteCount == 0) voteValue.emoji else voteValue.emoji + " " + voteCount)
          .setCallbackData(voteValue.name)
    }

    return InlineKeyboardMarkup().setKeyboard(
        listOf(
            listOf(
                createVoteInlineKeyboardButton(UP, stats.getOrDefault(UP, 0)),
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
