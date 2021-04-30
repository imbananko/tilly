package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.Image
import com.chsdngm.tilly.model.Meme
import com.chsdngm.tilly.model.MemeUpdate
import com.chsdngm.tilly.model.PrivateVoteValue.APPROVE
import com.chsdngm.tilly.model.PrivateVoteValue.DECLINE
import com.chsdngm.tilly.model.TelegramUser
import com.chsdngm.tilly.repository.ImageRepository
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.repository.PrivateModeratorRepository
import com.chsdngm.tilly.repository.UserRepository
import com.chsdngm.tilly.similarity.GoogleImageRecognizer
import com.chsdngm.tilly.similarity.ImageMatcher
import com.chsdngm.tilly.utility.TillyConfig.Companion.BETA_CHAT_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.BOT_TOKEN
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHANNEL_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHAT_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.api
import com.chsdngm.tilly.utility.createMarkup
import com.chsdngm.tilly.utility.isFromChat
import com.chsdngm.tilly.utility.setChatId
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.ForwardMessage
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember
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
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import javax.annotation.PostConstruct

@Service
class MemeHandler(
    private val userRepository: UserRepository,
    private val imageMatcher: ImageMatcher,
    private val imageRecognizer: GoogleImageRecognizer,
    private val imageRepository: ImageRepository,
    private val privateModeratorRepository: PrivateModeratorRepository,
    private val memeRepository: MemeRepository,
) : AbstractHandler<MemeUpdate> {
  private val log = LoggerFactory.getLogger(javaClass)

  private val natashaId = 117901733
  private fun MemeUpdate.isFromNatasha() = this.user.id == natashaId

  fun replyToNatasha(update: MemeUpdate): Message =
      SendMessage()
          .setChatId(natashaId)
          .setReplyToMessageId(update.messageId)
          .setText("Мем на привитой модерации")
          .let { api.execute(it) }

  private fun sendNatashaEventToBeta(update: MemeUpdate) =
      SendPhoto()
          .setChatId(BETA_CHAT_ID)
          .setPhoto(update.fileId)
          .setCaption("мем Натахи отправлен на личную модерацию в НИКУДА")
          .setParseMode(ParseMode.HTML)
          .let { api.execute(it) }

  override fun handle(update: MemeUpdate) {
    update.file = download(update.fileId)

    if (update.isFromNatasha()) {
      replyToNatasha(update)
      sendNatashaEventToBeta(update)
      return
    }

    update.isFreshman = !userRepository.existsById(update.user.id)

    val memeSender = TelegramUser(update.user.id, update.user.userName, update.user.firstName, update.user.lastName)
    userRepository.save(memeSender)


    imageMatcher.tryFindDuplicate(update.file)?.also { duplicateFileId ->
      handleDuplicate(update, duplicateFileId)
    } ?: run {
      val currentModerators = privateModeratorRepository.findCurrentModeratorsIds()

      if (update.newMemeStatus.canBeScheduled()
          && !update.isFreshman
          && currentModerators.size < 5
          && memeRepository.count().toInt() % 5 == 0) {

        userRepository
            .findTopSenders(memeSender.id)
            .firstOrNull { potentialModerator -> !currentModerators.contains(potentialModerator.id) }
            ?.let { newModerator ->
              moderateWithUser(update, newModerator.id.toLong()).also { meme ->
                log.info("sent for moderation to user=$newModerator. meme=$meme")
                privateModeratorRepository.addPrivateModerator(newModerator.id)
                sendPrivateModerationEventToBeta(meme, memeSender, newModerator)
              }
            } ?: run {

          log.info("cannot perform ranked moderation. unable to pick moderator")
          moderateWithGroup(update)
        }

      } else {
        moderateWithGroup(update)
      }
      imageMatcher.add(update.fileId, update.file)
      imageRepository.save(imageRecognizer.enrich(Image(update.fileId, update.file.readBytes(), hash = imageMatcher.calculateHash(update.file))))
    }
    log.info("processed meme update=$update")
  }

  fun handleDuplicate(update: MemeUpdate, duplicateFileId: String) {
    sendSorryText(update)

    memeRepository.findByFileId(duplicateFileId)?.also { meme ->
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
          .setCaption(runCatching { resolveCaption(update) }.getOrNull())
          .setParseMode(ParseMode.HTML)
          .setReplyMarkup(createMarkup(emptyMap())).let { api.execute(it) }.let { sent ->
            val senderMessageId = replyToSender(update).messageId
            memeRepository.save(Meme(CHAT_ID, sent.messageId, update.user.id, update.newMemeStatus, senderMessageId, update.fileId, update.caption))
          }.also {
            log.info("sent for moderation to group chat. meme=$it")
          }

  fun moderateWithUser(update: MemeUpdate, moderatorId: Long): Meme =
      SendPhoto()
          .setChatId(moderatorId)
          .setPhoto(update.fileId)
          .setCaption(update.caption?.let { it + "\n\n"} ?: "" + "Теперь ты модератор!")
          .setParseMode(ParseMode.HTML)
          .setReplyMarkup(createPrivateModerationMarkup()).let { api.execute(it) }.let { sent ->
            val senderMessageId = replyToSenderAboutPrivateModeration(update).messageId
            memeRepository.save(Meme(moderatorId, sent.messageId, update.user.id, update.newMemeStatus, senderMessageId, update.fileId, update.caption))
          }

  fun resolveCaption(update: MemeUpdate): String =
      update.caption ?: "" +
      if (GetChatMember()
              .setChatId(CHAT_ID)
              .setUserId(update.user.id).let { api.execute(it) }
              .isFromChat()) ""
      else
        "\n\nSender: ${update.senderName}" +
        if (update.isFreshman) "\n\n#freshman" else ""

  private fun forwardMemeFromChannelToUser(meme: Meme, user: User) =
      api.execute(ForwardMessage()
          .setChatId(user.id)
          .setFromChatId(CHANNEL_ID)
          .setMessageId(meme.channelMessageId)
          .disableNotification())


  private fun forwardMemeFromChatToUser(meme: Meme, user: User) =
      api.execute(ForwardMessage()
          .setChatId(user.id)
          .setFromChatId(meme.moderationChatId)
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
          .setText("мем на приватной модерации"))

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
          .setCaption("мем авторства ${memeSender.mention()} отправлен на личную модерацию к ${moderator.mention()}")
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
