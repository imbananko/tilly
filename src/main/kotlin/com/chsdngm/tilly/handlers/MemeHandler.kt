package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.MemeEntity
import com.chsdngm.tilly.model.MemeUpdate
import com.chsdngm.tilly.repository.ImageRepository
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.repository.UserRepository
import com.chsdngm.tilly.similarity.MemeMatcher
import com.chsdngm.tilly.utility.BotConfig
import com.chsdngm.tilly.utility.BotConfigImpl
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
import javax.annotation.PostConstruct
import javax.imageio.ImageIO
import kotlin.system.measureTimeMillis

@Component
class MemeHandler(private val memeRepository: MemeRepository,
                  private val imageRepository: ImageRepository,
                  private val userRepository: UserRepository,
                  private val memeMatcher: MemeMatcher,
                  private val botConfig: BotConfigImpl) : AbstractHandler<MemeUpdate>(), BotConfig by botConfig {

  private val log = LoggerFactory.getLogger(javaClass)

  @PostConstruct
  private fun init() {
    log.info("Start loading memes into matcher")
    measureTimeMillis {
      imageRepository.findAll().forEach { memeMatcher.add(it.key, ImageIO.read(it.value)) }
    }.also { log.info("Finished loading memes into matcher. took: $it ms") }
  }

  override fun handle(update: MemeUpdate) {
    runCatching {
      userRepository.saveIfNotExists(update.user)
      val file = downloadFromFileId(update.fileId)

      memeMatcher.tryFindDuplicate(file)?.also { duplicate ->
        sendSorryText(update)
        memeRepository.findByFileId(duplicate)?.also { meme ->
          meme.channelMessageId?.apply { forwardMemeFromChannelToUser(meme, update.user) }
              ?: forwardMemeFromChatToUser(meme, update.user)
          runCatching { sendDuplicateToBeta(update.senderName, duplicateFileId = update.fileId, originalFileId = meme.fileId) }
              .onFailure { throwable -> log.error("Can't send duplicate info to beta chat", throwable) }
        }
      } ?: sendMemeToChat(update).let { sent ->
        val privateMessageId = sendReplyToMeme(update).messageId
        memeRepository.save(MemeEntity(sent.messageId, update.user.id, update.fileId, update.caption, privateMessageId)).also {
          imageRepository.saveImage(file, it.fileId)
          memeMatcher.add(it.fileId, file)
          log.info("Sent meme=$it to chat")
        }
      }
    }.onFailure { throwable ->
      log.error("Failed to check duplicates for update=$update", throwable)
    }
  }

  fun sendMemeToChat(update: MemeUpdate): Message =
      execute(SendPhoto()
          .setChatId(chatId)
          .setPhoto(update.fileId)
          .setCaption(runCatching { resolveCaption(update) }.getOrNull())
          .setParseMode(ParseMode.HTML)
          .setReplyMarkup(createMarkup(emptyMap())))

  fun resolveCaption(update: MemeUpdate): String =
      update.caption ?: "" +
      if (GetChatMember()
              .setChatId(chatId)
              .setUserId(update.user.id).let { execute(it) }
              .isFromChat()) ""
      else "\n\nSender: ${update.senderName}"

  private fun forwardMemeFromChannelToUser(meme: MemeEntity, user: User) {
    execute(ForwardMessage()
        .setChatId(user.id)
        .setMessageId(meme.channelMessageId)
        .disableNotification())
    log.info("Successfully forwarded original meme to sender=${user.id}. $meme")
  }

  private fun forwardMemeFromChatToUser(meme: MemeEntity, user: User) {
    execute(ForwardMessage()
        .setChatId(user.id)
        .setFromChatId(chatId)
        .setMessageId(meme.chatMessageId)
        .disableNotification())
    log.info("Successfully forwarded original meme to sender=${user.id}. $meme")
  }

  private fun sendSorryText(update: MemeUpdate) =
      execute(SendMessage()
          .setChatId(update.user.id)
          .setReplyToMessageId(update.messageId)
          .disableNotification()
          .setText("К сожалению, мем уже был отправлен ранее!"))

  fun sendReplyToMeme(update: MemeUpdate): Message =
      execute(SendMessage()
          .setChatId(update.user.id)
          .setReplyToMessageId(update.messageId)
          .disableNotification()
          .setText("мем на модерации"))

  private fun sendDuplicateToBeta(username: String, duplicateFileId: String, originalFileId: String) =
      execute(
          SendMediaGroup(
              betaChatId,
              listOf(
                  InputMediaPhoto(duplicateFileId, "дубликат, отправленный $username").setParseMode(ParseMode.HTML),
                  InputMediaPhoto(originalFileId, "оригинал")
              )
          )
      )

  fun downloadFromFileId(fileId: String): File =
      File.createTempFile("photo-", "-" + Thread.currentThread().id + "-" + System.currentTimeMillis()).apply { this.deleteOnExit() }.also {
        FileOutputStream(it).use { out ->
          URL(execute(GetFile().setFileId(fileId)).getFileUrl(botToken)).openStream().use { stream -> IOUtils.copy(stream, out) }
        }
      }.also {
        log.info("Successfully downloaded file=$it")
      }
}
