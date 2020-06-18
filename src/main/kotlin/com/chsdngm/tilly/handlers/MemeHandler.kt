package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.MemeEntity
import com.chsdngm.tilly.model.MemeUpdate
import com.chsdngm.tilly.repository.ImageRepository
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.repository.UserRepository
import com.chsdngm.tilly.similarity.MemeMatcher
import com.chsdngm.tilly.utility.BotConfig.Companion.BETA_CHAT_ID
import com.chsdngm.tilly.utility.BotConfig.Companion.BOT_TOKEN
import com.chsdngm.tilly.utility.BotConfig.Companion.CHAT_ID
import com.chsdngm.tilly.utility.BotConfig.Companion.api
import com.chsdngm.tilly.utility.isFromChat
import com.chsdngm.tilly.utility.setChatId
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
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
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import javax.annotation.PostConstruct
import javax.imageio.ImageIO
import kotlin.system.measureTimeMillis

@Component
class MemeHandler(private val memeRepository: MemeRepository,
                  private val imageRepository: ImageRepository,
                  private val userRepository: UserRepository,
                  private val memeMatcher: MemeMatcher) : AbstractHandler<MemeUpdate>() {

  private val log = LoggerFactory.getLogger(javaClass)
  private val memeCount = AtomicInteger(0)

  @PostConstruct
  private fun init() {
    log.info("Start loading memes into matcher")
    measureTimeMillis {
      imageRepository.findAll().apply { memeCount.set(this.size) }.forEach { memeMatcher.add(it.key, ImageIO.read(it.value)) }
    }.also { log.info("Finished loading memes into matcher. took: $it ms") }
  }

  override fun handle(update: MemeUpdate) {
    update.file = download(update.fileId)

    runCatching {
      userRepository.saveIfNotExists(update.user)

      memeMatcher.tryFindDuplicate(update.file)?.also {
        handleDuplicate(update)
      }
    }.onFailure {
      log.info("Failed to check duplicates for update=$update")
    }.getOrThrow() ?: runCatching {
      if (!isLocal(update.caption) &&
          memeCount.incrementAndGet() % 5 == 0 &&
          userRepository.isRankedModerationAvailable()) {
        log.info("Ranked moderation time!")

        val winnerId = userRepository.getTopSenders(5).keys.find { userRepository.tryPickUserForModeration(it) }

        if (winnerId != null)
          log.info("Picked userId=$winnerId")
        else
          log.info("User is already on list")

        moderateWithChat(update)
      } else {
        moderateWithChat(update)
      }
    }.onFailure {
      log.error("Failed to handle update=$update", it)
    }
  }

  fun handleDuplicate(update: MemeUpdate) {
    sendSorryText(update)

    memeRepository.findByFileId(update.fileId)?.also { meme ->
      if (meme.channelMessageId == null)
        forwardMemeFromChatToUser(meme, update.user)
      else
        forwardMemeFromChannelToUser(meme, update.user)
    }

    sendDuplicateToBeta(update.senderName, duplicateFileId = update.fileId, originalFileId = update.fileId)
  }

  fun moderateWithChat(update: MemeUpdate) {
    sendMemeToChat(update).let { sent ->
      val privateMessageId = sendReplyToMeme(update).messageId
      memeRepository.save(MemeEntity(sent.messageId, update.user.id, update.fileId, update.caption, privateMessageId)).also {
        log.info("Sent meme=$it to chat")
      }
      imageRepository.saveImage(update.file, update.fileId)
      memeMatcher.add(update.fileId, update.file)
    }
  }

  fun sendMemeToChat(update: MemeUpdate): Message =
      api.execute(SendPhoto()
          .setChatId(CHAT_ID)
          .setPhoto(update.fileId)
          .setCaption(runCatching { resolveCaption(update) }.getOrNull())
          .setParseMode(ParseMode.HTML)
          .setReplyMarkup(createMarkup(emptyMap())))

  fun resolveCaption(update: MemeUpdate): String =
      update.caption ?: "" +
      if (GetChatMember()
              .setChatId(CHAT_ID)
              .setUserId(update.user.id).let { api.execute(it) }
              .isFromChat()) ""
      else "\n\nSender: ${update.senderName}"

  private fun forwardMemeFromChannelToUser(meme: MemeEntity, user: User) {
    api.execute(ForwardMessage()
        .setChatId(user.id)
        .setMessageId(meme.channelMessageId)
        .disableNotification())
    log.info("Successfully forwarded original meme to sender=${user.id}. $meme")
  }

  private fun forwardMemeFromChatToUser(meme: MemeEntity, user: User) {
    api.execute(ForwardMessage()
        .setChatId(user.id)
        .setFromChatId(CHAT_ID)
        .setMessageId(meme.chatMessageId)
        .disableNotification())
    log.info("Successfully forwarded original meme to sender=${user.id}. $meme")
  }

  private fun sendSorryText(update: MemeUpdate) =
      api.execute(SendMessage()
          .setChatId(update.user.id)
          .setReplyToMessageId(update.messageId)
          .disableNotification()
          .setText("К сожалению, мем уже был отправлен ранее!"))

  fun sendReplyToMeme(update: MemeUpdate): Message =
      api.execute(SendMessage()
          .setChatId(update.user.id)
          .setReplyToMessageId(update.messageId)
          .disableNotification()
          .setText("мем на модерации"))

  private fun sendDuplicateToBeta(username: String, duplicateFileId: String, originalFileId: String) =
      SendMediaGroup(
          BETA_CHAT_ID,
          listOf(
              InputMediaPhoto(duplicateFileId, "дубликат, отправленный $username").setParseMode(ParseMode.HTML),
              InputMediaPhoto(originalFileId, "оригинал")
          )).let { api.execute(it) }

  fun download(fileId: String): File =
      File.createTempFile("photo-", "-" + Thread.currentThread().id + "-" + System.currentTimeMillis()).apply { this.deleteOnExit() }.also {
        FileOutputStream(it).use { out ->
          URL(api.execute(GetFile().setFileId(fileId)).getFileUrl(BOT_TOKEN)).openStream().use { stream -> IOUtils.copy(stream, out) }
        }
      }.also {
        log.info("Successfully downloaded file=$it")
      }
}
