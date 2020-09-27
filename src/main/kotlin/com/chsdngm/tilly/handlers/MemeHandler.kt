package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.Meme
import com.chsdngm.tilly.model.MemeUpdate
import com.chsdngm.tilly.model.PrivateVoteValue.APPROVE
import com.chsdngm.tilly.model.PrivateVoteValue.DECLINE
import com.chsdngm.tilly.model.TelegramUser
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.repository.UserRepository
import com.chsdngm.tilly.similarity.ImageMatcher
import com.chsdngm.tilly.utility.TillyConfig.Companion.BETA_CHAT_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.BOT_TOKEN
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHAT_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.api
import com.chsdngm.tilly.utility.createMarkup
import com.chsdngm.tilly.utility.hasLocalTag
import com.chsdngm.tilly.utility.setChatId
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.ForwardMessage
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import javax.annotation.PostConstruct

@Service
class MemeHandler(private val userRepository: UserRepository,
                  private val imageMatcher: ImageMatcher,
                  private val memeRepository: MemeRepository) : AbstractHandler<MemeUpdate> {

  private val log = LoggerFactory.getLogger(javaClass)
  private val memeCount = AtomicLong(0)

  @PostConstruct
  private fun init() {
    memeCount.set(memeRepository.count())
  }

  override fun handle(update: MemeUpdate) {
    update.file = download(update.fileId)

    val memeSender = TelegramUser(update.user.id, update.user.userName, update.user.firstName, update.user.lastName)
    userRepository.save(memeSender)

    imageMatcher.tryFindDuplicate(update.file)?.also {
      handleDuplicate(update)
    } ?: run {
      if (!hasLocalTag(update.caption)
          && memeCount.incrementAndGet() % 5 == 0L
          && userRepository.isRankedModerationAvailable()) {

        userRepository.findTopSenders(5, update.user.id).find { userRepository.tryPickUserForModeration(it.id) }?.let { moderator ->
          moderateWithUser(update, moderator.id.toLong()).also { meme ->
            log.info("sent for moderation to user=$moderator. meme=$meme")
            sendPrivateModerationEventToBeta(meme, memeSender, moderator)
          }
        } ?: run {
          log.info("cannot perform ranked moderation. unable to pick moderator")
          moderateWithGroup(update).also { log.info("sent for moderation to group chat. meme=$it") }
        }

      } else {
        val meme = moderateWithGroup(update)
        log.info("sent for moderation to group chat. meme=$meme")
      }
      imageMatcher.add(update.fileId, update.file)
    }
    log.info("processed meme update=$update")
  }

  fun handleDuplicate(update: MemeUpdate) {
    sendSorryText(update)

    memeRepository.findByFileId(update.fileId)?.also { meme ->
      if (meme.channelMessageId == null)
        forwardMemeFromChatToUser(meme, update.user)
      else
        forwardMemeFromChannelToUser(meme, update.user)
      log.info("successfully forwarded original meme to sender=${update.user.id}. $meme")
      sendDuplicateToBeta(update.senderName, duplicateFileId = update.fileId, originalFileId = meme.fileId)
    }
  }

  fun moderateWithGroup(update: MemeUpdate): Meme =
      SendPhoto()
          .setChatId(CHAT_ID)
          .setPhoto(update.fileId)
          .setCaption(update.caption?.let { it + "\n\n" } ?: "" + "Отправитель: ${update.senderName}")
          .setParseMode(ParseMode.HTML)
          .setReplyMarkup(createMarkup(emptyMap())).let { api.execute(it) }.let { sent ->
            val senderMessageId = replyToSender(update).messageId
            memeRepository.save(Meme(CHAT_ID, sent.messageId, update.user.id, senderMessageId, update.fileId, update.caption))
          }

  fun moderateWithUser(update: MemeUpdate, moderatorId: Long): Meme =
      SendPhoto()
          .setChatId(moderatorId)
          .setPhoto(update.fileId)
          .setCaption(update.caption?.let { it + "\n\n" }
              ?: "" + "Ты был выбран модератором на основе рейтинга за последние 3 дня")
          .setParseMode(ParseMode.HTML)
          .setReplyMarkup(createPrivateModerationMarkup()).let { api.execute(it) }.let { sent ->
            val senderMessageId = replyToSenderAboutPrivateModeration(update).messageId
            memeRepository.save(Meme(moderatorId, sent.messageId, update.user.id, senderMessageId, update.fileId, update.caption))
          }

  private fun forwardMemeFromChannelToUser(meme: Meme, user: User) =
      api.execute(ForwardMessage()
          .setChatId(user.id)
          .setMessageId(meme.channelMessageId)
          .disableNotification())


  private fun forwardMemeFromChatToUser(meme: Meme, user: User) =
      api.execute(ForwardMessage()
          .setChatId(user.id)
          .setFromChatId(CHAT_ID)
          .setMessageId(meme.moderationChatMessageId)
          .disableNotification())


  private fun sendSorryText(update: MemeUpdate) =
      api.execute(SendMessage()
          .setChatId(update.user.id)
          .setReplyToMessageId(update.messageId)
          .disableNotification()
          .setText("К сожалению, мем уже был отправлен ранее!"))

  fun replyToSender(update: MemeUpdate): Message =
      api.execute(SendMessage()
          .setChatId(update.user.id)
          .setReplyToMessageId(update.messageId)
          .disableNotification()
          .setText("мем на модерации"))

  fun replyToSenderAboutPrivateModeration(update: MemeUpdate): Message =
      api.execute(SendMessage()
          .setChatId(update.user.id)
          .setReplyToMessageId(update.messageId)
          .disableNotification()
          .setText("мем на приватной модерации у одного из подписчиков канала"))

  private fun sendDuplicateToBeta(username: String, duplicateFileId: String, originalFileId: String) =
      SendMediaGroup(
          BETA_CHAT_ID,
          listOf(
              InputMediaPhoto(duplicateFileId, "дубликат, отправленный $username").setParseMode(ParseMode.HTML),
              InputMediaPhoto(originalFileId, "оригинал")
          )).let { api.execute(it) }

  private fun sendPrivateModerationEventToBeta(meme: Meme, memeSender: TelegramUser, moderator: TelegramUser) =
      SendPhoto()
          .setChatId(BETA_CHAT_ID)
          .setPhoto(meme.fileId)
          .setCaption("мем авторства ${memeSender.mention()} отправлен на приватную модерацию к ${moderator.mention()}")
          .setParseMode(ParseMode.HTML)
          .let { api.execute(it) }

  fun download(fileId: String): File =
      File.createTempFile("photo-", "-" + Thread.currentThread().id + "-" + System.currentTimeMillis()).apply { this.deleteOnExit() }.also {
        FileOutputStream(it).use { out ->
          URL(api.execute(GetFile().setFileId(fileId)).getFileUrl(BOT_TOKEN)).openStream().use { stream -> IOUtils.copy(stream, out) }
        }
      }.also {
        log.info("successfully downloaded file=$it")
      }

  fun createPrivateModerationMarkup() = InlineKeyboardMarkup(listOf(
      listOf(InlineKeyboardButton("Отправить на канал ${APPROVE.emoji}").also { it.callbackData = APPROVE.name }),
      listOf(InlineKeyboardButton("Предать забвению ${DECLINE.emoji}").also { it.callbackData = DECLINE.name })
  ))

}
