package com.imbananko.tilly

import com.imbananko.tilly.model.MemeEntity
import com.imbananko.tilly.model.MemeStatsEntry
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
import org.telegram.telegrambots.meta.api.methods.ForwardMessage
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import java.io.File.createTempFile
import java.io.FileOutputStream
import java.lang.System.currentTimeMillis
import java.lang.Thread.currentThread
import java.net.URL
import javax.annotation.PostConstruct

@EnableScheduling
@Component
class MemeManager(private val memeRepository: MemeRepository, private val voteRepository: VoteRepository, private val memeMatcher: MemeMatcher) : TelegramLongPollingBot() {

  private val log = LoggerFactory.getLogger(javaClass)

  @Value("\${target.chat.id}")
  private val chatId: Long = 0

  @Value("\${target.channel.id}")
  private val channelId: Long = 0

  @Value("\${bot.token}")
  private lateinit var token: String

  @Value("\${bot.username}")
  private lateinit var username: String

  override fun getBotToken(): String? = token

  override fun getBotUsername(): String? = username

  @PostConstruct
  private fun init() {
    memeRepository
        .findAllChatMemes(chatId)
        .distinctBy { it.fileId }
        .parallelStream()
        .map { meme -> Pair(meme.fileId, downloadFromFileId(meme.fileId)) }
        .forEach { (fileId, file) ->
          runCatching {
            memeMatcher.addMeme(fileId, file)
          }.onFailure { throwable: Throwable ->
            log.error("Failed to load file {}. Exception=", fileId, throwable)
          }
        }
  }

  @Scheduled(cron = "0 0 19 * * WED")
  private fun sendMemeOfTheWeek() {
    runCatching {
      val meme: MemeEntity? = memeRepository.findMemeOfTheWeek(channelId)

      meme ?: return

      val winner = execute(
          GetChatMember()
              .setChatId(channelId)
              .setUserId(meme.senderId))
          .user
      val congratulationText = "Поздравляем ${winner.mention()} с мемом недели!"
      val memeOfTheWeekMessage = runCatching {
        execute(
            SendMessage()
                .setChatId(channelId)
                .setParseMode(ParseMode.MARKDOWN)
                .setReplyToMessageId(meme.channelMessageId)
                .setText(congratulationText)
        )
      }.getOrElse { error ->
        log.error("Can't reply to meme of the week because of", error)
        log.info("Send meme of the week as new message")

        val statistics = voteRepository.getVotes(meme)
            .groupingBy { vote -> vote.voteValue }
            .eachCount()
        val fallbackMemeOfTheWeekMessage = execute(
            SendPhoto()
                .setChatId(channelId)
                .setPhoto(meme.fileId)
                .setParseMode(ParseMode.MARKDOWN)
                .setCaption(congratulationText)
                .setReplyMarkup(createMarkup(statistics))
        )

        memeRepository.update(meme, meme.copy(channelId = meme.channelId, channelMessageId = fallbackMemeOfTheWeekMessage.messageId))

        fallbackMemeOfTheWeekMessage
      }
      execute(PinChatMessage(memeOfTheWeekMessage.chatId, memeOfTheWeekMessage.messageId))
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
      execute(SendMessage(channelId, "Топ мемсы прошедшего года:"))
      execute(
          SendMediaGroup(channelId, memeRepository.findMemesOfTheYear(channelId).map { meme ->
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
    if (update.hasChannelVote()) processChannelVote(update)
    if (update.hasChatVote()) processChatVote(update)
    if (update.hasStatsCommand()) sendStats(update)
  }

  private fun updateCaptionInSenderChat(meme: MemeEntity, caption: String) =
      runCatching {
        execute(EditMessageCaption()
            .setChatId(meme.senderId.toString())
            .setMessageId(meme.privateMessageId)
            .setCaption(caption)
        )
      }.onFailure {
        log.error("Failed update caption in private chat=$chatId", it)
      }

  private fun processChatVote(update: Update) {
    val messageId = update.callbackQuery.message.messageId

    val meme = memeRepository.findMemeByChat(chatId, messageId) ?: return
    val vote = VoteEntity(chatId, messageId, update.callbackQuery.from.id, update.extractVoteValue())

    if (update.callbackQuery.message.isOld() || meme.senderId == vote.voterId) return

    val votes = voteRepository.getVotes(meme)
        .associate { Pair(it.voterId, it.voteValue) }
        .toMutableMap().also {
          it.merge(vote.voterId, vote.voteValue) { old, new -> if (old == new) null else new }
        }

    val groupedUpVotes = votes.values.groupingBy { it }.eachCount()
    val markup = createMarkup(groupedUpVotes)

    runCatching {
      execute(EditMessageReplyMarkup()
          .setChatId(chatId)
          .setMessageId(messageId)
          .setReplyMarkup(markup))
    }.onFailure {
      log.error("Failed to process vote=$vote", it)
    }

    var privateCaptionPrefix = "мем на модерации. статистика: \n\n"

    if (meme.channelId == null && readyForShipment(votes)) {
      runCatching {
        val originalCaption = update.callbackQuery.message.caption ?: ""
        val captionForChannel = originalCaption.split("Sender: ").firstOrNull()
        execute(SendPhoto()
            .setChatId(channelId)
            .setPhoto(meme.fileId)
            .setCaption(captionForChannel)
            .setReplyMarkup(markup)
        )
      }.onSuccess { message ->
        memeRepository.update(meme, meme.copy(channelId = message.chatId, channelMessageId = message.messageId))
        log.info("Sent meme to channel=$meme")
        privateCaptionPrefix = "мем отправлен на канал. статистика: \n\n"
      }.onFailure {
        log.error("Failed to send meme=$meme to channel", it)
        return
      }
    } else if (meme.channelId != null) {
      runCatching {
        execute(EditMessageReplyMarkup()
            .setChatId(meme.channelId)
            .setMessageId(meme.channelMessageId)
            .setReplyMarkup(markup)
        )
      }.onFailure {
        log.error("Failed to process vote=$vote", it)
        return
      }
    }

    if (votes.containsKey(vote.voterId)) voteRepository.insertOrUpdate(vote) else voteRepository.delete(vote)

    val caption = groupedUpVotes.entries.joinToString(
        prefix = privateCaptionPrefix,
        transform = { (value, sum) -> "${value.emoji}: $sum" })
    updateCaptionInSenderChat(meme, caption)

    log.info("Processed chat vote=$vote")
  }

  private fun processChannelVote(update: Update) {
    val messageId = update.callbackQuery.message.messageId

    val meme = memeRepository.findMemeByChannel(channelId, messageId) ?: return
    val vote = VoteEntity(meme.chatId, meme.messageId, update.callbackQuery.from.id, update.extractVoteValue())

    if (update.callbackQuery.message.isOld() || meme.senderId == vote.voterId) return

    val votes = voteRepository.getVotes(meme)
        .associate { Pair(it.voterId, it.voteValue) }
        .toMutableMap().also {
          it.merge(vote.voterId, vote.voteValue) { old, new -> if (old == new) null else new }
        }

    runCatching {
      val markup = createMarkup(votes.values.groupingBy { it }.eachCount())
      execute(
          EditMessageReplyMarkup()
              .setChatId(channelId)
              .setMessageId(messageId)
              .setReplyMarkup(markup)
      )
      if (meme.chatId != meme.channelId) {
        execute(
            EditMessageReplyMarkup()
                .setChatId(meme.chatId)
                .setMessageId(meme.messageId)
                .setReplyMarkup(markup))
      }
    }.onFailure { throwable ->
      log.error("Failed to process vote=" + vote + ". Exception=" + throwable.message)
      return
    }.onSuccess {
      if (votes.containsKey(vote.voterId)) voteRepository.insertOrUpdate(vote) else voteRepository.delete(vote)

      val caption = votes.values.groupingBy { it }.eachCount().entries.joinToString(
          prefix = "мем отправлен на канал. статистика: \n\n",
          transform = { (value, sum) -> "${value.emoji}: $sum" })
      updateCaptionInSenderChat(meme, caption)

      log.info("Processed channel vote=$vote")
    }
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
              .setText(formatStatsMessage(voteRepository.getStatsByUser(channelId, update.message.from.id)))
      )
    }.onSuccess {
      log.debug("Sent stats to user=${update.message.from.id}")
    }.onFailure { throwable ->
      log.error("Failed to send stats to user=${update.message.from.id}. Exception=", throwable)
    }
  }

  private fun processMeme(update: Update) {
    val message = update.message
    val senderId = message.from.id
    val fileId = message.photo[0].fileId
    val isChatMember = runCatching { execute(GetChatMember().setChatId(chatId).setUserId(senderId)).status }
        .getOrElse { ex ->
          log.error("can't get chat status of user $senderId because of", ex)
          "unknown"
        }.isChatUserStatus()
    val caption = (message.caption?.trim()?.run { this + if (isChatMember) "" else "\n\n" } ?: "") +
        if (isChatMember) ""
        else "Sender: " + (message.from.userName?.let { "@$it" } ?: message.from.firstName ?: message.from.lastName)

    val privateMessageId =
        runCatching {
          execute(DeleteMessage()
              .setChatId(message.chatId)
              .setMessageId(message.messageId))

          execute(SendPhoto()
              .setPhoto(fileId)
              .setChatId(message.chatId)
              .setCaption(message.caption))
        }.getOrThrow().messageId

    fun sendMemeToChat() =
        runCatching {
          execute(
              SendPhoto()
                  .setChatId(chatId)
                  .setPhoto(fileId)
                  .setCaption(caption)
                  .setReplyMarkup(createMarkup(emptyMap())))
        }.onSuccess { sentMessage ->
          memeRepository.save(MemeEntity(chatId, sentMessage.messageId, senderId, fileId, privateMessageId)).also {
            downloadFromFileId(it.fileId).also { file ->
              log.info(file.toString())
              memeMatcher.addMeme(it.fileId, downloadFromFileId(it.fileId))
            }
            log.info("Sent meme=$it to chat")
          }
        }.onFailure {
          log.error("Failed to send meme from message=${message.print()}. Exception=", it)
        }

    fun forwardOriginalMemeToSender(originalFileId: String) =
        runCatching {
          execute(SendMessage()
              .setChatId(update.message.chatId)
              .setReplyToMessageId(update.message.messageId)
              .setText("К сожалению, мем уже был отправлен ранее!"))
          memeRepository.findMeme(originalFileId)?.also {
            if (it.isPublishedOnChannel())
              execute(ForwardMessage()
                  .setChatId(update.message.chatId)
                  .setFromChatId(it.channelId)
                  .setMessageId(it.channelMessageId)
                  .disableNotification()
              )
            else
              execute(ForwardMessage()
                  .setChatId(update.message.chatId)
                  .setFromChatId(it.chatId)
                  .setMessageId(it.messageId)
                  .disableNotification())
          }
        }.onFailure {
          log.error("Failed to forward original meme. Exception=", it)
        }.onSuccess {
          log.info("Successfully forwarded original meme to sender=${message.from.mention()}. $it")
        }

    runCatching {
      downloadFromFileId(fileId).also {
        memeMatcher.tryFindDuplicate(it)?.also { duplicateFileId -> forwardOriginalMemeToSender(duplicateFileId) }
            ?: sendMemeToChat()
      }
    }.onFailure { throwable ->
      log.error("Failed to check duplicates for fileId=$fileId. Message=${message.print()}. Exception=", throwable)
    }
  }

  private fun readyForShipment(votes: MutableMap<Int, VoteValue>): Boolean =
      votes.values.filter { it == UP }.size - votes.values.filter { it == DOWN }.size >= 5

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
                createVoteInlineKeyboardButton(DOWN, stats.getOrDefault(DOWN, 0))
            )
        )
    )
  }

  private fun downloadFromFileId(fileId: String) =
      createTempFile("photo-", "-" + currentThread().id + "-" + currentTimeMillis()).apply { this.deleteOnExit() }.also {
        FileOutputStream(it).use { out ->
          URL(execute(GetFile().setFileId(fileId)).getFileUrl(botToken)).openStream().use { stream -> IOUtils.copy(stream, out) }
        }
      }.also {
        log.info("Successfully downloaded file=$it")
      }
}
