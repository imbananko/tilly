package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.*
import com.chsdngm.tilly.model.PrivateVoteValue.APPROVE
import com.chsdngm.tilly.model.PrivateVoteValue.DECLINE
import com.chsdngm.tilly.repository.ImageRepository
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.repository.PrivateModeratorRepository
import com.chsdngm.tilly.repository.UserRepository
import com.chsdngm.tilly.similarity.AnalyzingResults
import com.chsdngm.tilly.similarity.ImageMatcher
import com.chsdngm.tilly.similarity.ImageTextRecognizer
import com.chsdngm.tilly.utility.TillyConfig
import com.chsdngm.tilly.utility.TillyConfig.Companion.BETA_CHAT_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.BOT_TOKEN
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHANNEL_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.CHAT_ID
import com.chsdngm.tilly.utility.TillyConfig.Companion.api
import com.chsdngm.tilly.utility.createMarkup
import com.chsdngm.tilly.utility.isFromChat
import com.chsdngm.tilly.utility.logExceptionInBetaChat
import org.apache.commons.io.IOUtils
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.ForwardMessage
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import java.io.File
import java.io.FileOutputStream
import java.net.URL

@Service
class MemeHandler(
    private val userRepository: UserRepository,
    private val imageMatcher: ImageMatcher,
    private val imageTextRecognizer: ImageTextRecognizer,
    private val imageRepository: ImageRepository,
    private val privateModeratorRepository: PrivateModeratorRepository,
    private val memeRepository: MemeRepository,
    private val elasticsearchClient: RestHighLevelClient
) : AbstractHandler<MemeUpdate> {
    private val log = LoggerFactory.getLogger(javaClass)
    private val textFieldName = "text"
    private val indexDocumentType = "_doc"

    override fun handle(update: MemeUpdate) {
        update.file = download(update.fileId)
        update.isFreshman = !userRepository.existsById(update.user.id.toInt())

        val memeSender = userRepository.findById(update.user.id.toInt()).let {
            userRepository.save(
                TelegramUser(
                    update.user.id.toInt(),
                    update.user.userName,
                    update.user.firstName,
                    update.user.lastName,
                    if (it.isEmpty) UserStatus.DEFAULT else it.get().status
                )
            )
        }

        if (memeSender.status == UserStatus.BANNED) {
            replyToBannedUser(update)
            sendBannedEventToBeta(update, memeSender)
            return
        }

        imageMatcher.tryFindDuplicate(update.file)?.also { duplicateFileId ->
            handleDuplicate(update, duplicateFileId)
        } ?: run {
            val currentModerators = privateModeratorRepository.findCurrentModeratorsIds()

            if (update.newMemeStatus.canBeScheduled()
                && !update.isFreshman
                && currentModerators.size < 5
                && memeRepository.count().toInt() % 5 == 0
            ) {
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

            val analyzingResults: AnalyzingResults? = runCatching { imageTextRecognizer.analyze(update.file) }
                    .onFailure { logExceptionInBetaChat(it) }
                    .getOrNull()

            val image = Image(
                update.fileId,
                update.file.readBytes(),
                hash = imageMatcher.calculateHash(update.file),
                words = analyzingResults?.words,
                labels = analyzingResults?.labels
            )

            imageMatcher.add(image)
            imageRepository.save(image)

            if (analyzingResults?.words?.isNotEmpty() == true) {
                val text = analyzingResults.words.joinToString(separator = " ")
                val indexRequest =
                        IndexRequest(TillyConfig.ELASTICSEARCH_INDEX_NAME)
                                .id(image.fileId)
                                .type(indexDocumentType)
                                .source(textFieldName, text)
                                .timeout(TillyConfig.ELASTICSEARCH_REQUEST_TIMEOUT)
                elasticsearchClient.index(indexRequest, RequestOptions.DEFAULT)
            }
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

    fun moderateWithGroup(update: MemeUpdate): Unit =
        SendPhoto().apply {
            chatId = CHAT_ID
            photo = InputFile(update.fileId)
            caption = runCatching { resolveCaption(update) }.getOrNull()
            parseMode = ParseMode.HTML
            replyMarkup = createMarkup(emptyMap())
        }.let {
            api.execute(it)
        }.let { sent ->

            val senderMessageId = replyToSender(update).messageId
            memeRepository.save(
                Meme(
                    CHAT_ID.toLong(),
                    sent.messageId,
                    update.user.id.toInt(),
                    update.newMemeStatus,
                    senderMessageId,
                    update.fileId,
                    update.caption
                )
            ).also { log.info("sent for moderation to group chat. meme=$it") }
        }

    private fun moderateWithUser(update: MemeUpdate, moderatorId: Long): Meme =
        SendPhoto().apply {
            chatId = moderatorId.toString()
            photo = InputFile(update.fileId)
            caption = ((update.caption?.let { it + "\n\n" } ?: ("" + "Теперь ты модератор!")))
            parseMode = ParseMode.HTML
            replyMarkup = createPrivateModerationMarkup()
        }.let { api.execute(it) }.let { sent ->
            val senderMessageId = replyToSenderAboutPrivateModeration(update).messageId
            memeRepository.save(
                Meme(
                    moderatorId,
                    sent.messageId,
                    update.user.id.toInt(),
                    update.newMemeStatus,
                    senderMessageId,
                    update.fileId,
                    update.caption
                )
            )
        }

    private fun resolveCaption(update: MemeUpdate): String =
        update.caption ?: ("" +
                if (GetChatMember().apply {
                        chatId = CHAT_ID
                        userId = update.user.id
                    }.let {
                        api.execute(it)
                    }.isFromChat()) ""
                else
                    "\n\nSender: ${update.senderName}" +
                            if (update.isFreshman) "\n\n#freshman" else "")

    private fun forwardMemeFromChannelToUser(meme: Meme, user: User) =
        ForwardMessage().apply {
            chatId = user.id.toString()
            fromChatId = CHANNEL_ID
            messageId = meme.channelMessageId!!
            disableNotification = true
        }.let { api.execute(it) }

    private fun forwardMemeFromChatToUser(meme: Meme, user: User) =
        ForwardMessage().apply {
            chatId = user.id.toString()
            fromChatId = meme.moderationChatId.toString()
            messageId = meme.moderationChatMessageId
            disableNotification = true
        }.let { api.execute(it) }

    private fun sendSorryText(update: MemeUpdate) =
        SendMessage().apply {
            chatId = update.user.id.toString()
            replyToMessageId = update.messageId
            disableNotification = true
            text = "К сожалению, мем уже был отправлен ранее!"
        }.let { api.execute(it) }

    private fun replyToSender(update: MemeUpdate): Message =
        SendMessage().apply {
            chatId = update.user.id.toString()
            replyToMessageId = update.messageId
            disableNotification = true
            text = "мем на модерации"
        }.let { api.execute(it) }

    private fun replyToSenderAboutPrivateModeration(update: MemeUpdate): Message =
        SendMessage().apply {
            chatId = update.user.id.toString()
            replyToMessageId = update.messageId
            disableNotification = true
            text = "мем на приватной модерации"
        }.let { api.execute(it) }

    private fun sendDuplicateToBeta(username: String, duplicateFileId: String, originalFileId: String) =
        SendMediaGroup().apply {
            chatId = BETA_CHAT_ID
            medias = listOf(
                InputMediaPhoto().apply {
                    media = duplicateFileId
                    caption = "дубликат, отправленный $username"
                    parseMode = ParseMode.HTML
                },
                InputMediaPhoto().apply {
                    media = originalFileId
                    caption = "оригинал"
                })
            disableNotification = true
        }.let { api.execute(it) }

    private fun sendPrivateModerationEventToBeta(meme: Meme, memeSender: TelegramUser, moderator: TelegramUser) =
        SendPhoto().apply {
            chatId = BETA_CHAT_ID
            photo = InputFile(meme.fileId)
            caption = "мем авторства ${memeSender.mention()} отправлен на личную модерацию к ${moderator.mention()}"
            parseMode = ParseMode.HTML
            disableNotification = true
        }.let { api.execute(it) }

    private fun download(fileId: String): File =
        File.createTempFile("photo-", "-" + Thread.currentThread().id + "-" + System.currentTimeMillis())
            .apply { this.deleteOnExit() }.also {
                FileOutputStream(it).use { out ->
                    URL(api.execute(GetFile(fileId)).getFileUrl(BOT_TOKEN)).openStream()
                        .use { stream -> IOUtils.copy(stream, out) }
                }
            }.also {
                log.info("successfully downloaded file=$it")
            }

    fun createPrivateModerationMarkup() = InlineKeyboardMarkup(
        listOf(
            listOf(InlineKeyboardButton("Отправить на канал ${APPROVE.emoji}").also { it.callbackData = APPROVE.name }),
            listOf(InlineKeyboardButton("Предать забвению ${DECLINE.emoji}").also { it.callbackData = DECLINE.name })
        )
    )

    private fun replyToBannedUser(update: MemeUpdate): Message =
        SendMessage().apply {
            chatId = update.user.id.toString()
            replyToMessageId = update.messageId
            text = "Мем на привитой модерации"
        }.let { api.execute(it) }

    private fun sendBannedEventToBeta(update: MemeUpdate, telegramUser: TelegramUser) =
        SendPhoto().apply {
            chatId = BETA_CHAT_ID
            photo = InputFile(update.fileId)
            caption = "мем ${telegramUser.mention()} отправлен на личную модерацию в НИКУДА"
            parseMode = ParseMode.HTML
            disableNotification = true

        }.let { api.execute(it) }
}
