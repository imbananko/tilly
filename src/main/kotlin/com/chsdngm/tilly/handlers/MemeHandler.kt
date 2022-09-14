package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.config.TelegramConfig
import com.chsdngm.tilly.config.TelegramConfig.Companion.BETA_CHAT_ID
import com.chsdngm.tilly.config.TelegramConfig.Companion.BOT_TOKEN
import com.chsdngm.tilly.config.TelegramConfig.Companion.CHANNEL_ID
import com.chsdngm.tilly.config.TelegramConfig.Companion.CHAT_ID
import com.chsdngm.tilly.config.TelegramConfig.Companion.api
import com.chsdngm.tilly.model.*
import com.chsdngm.tilly.model.MemeStatus.LOCAL
import com.chsdngm.tilly.model.PrivateVoteValue.*
import com.chsdngm.tilly.model.DistributedModerationVoteValue.*
import com.chsdngm.tilly.model.dto.Image
import com.chsdngm.tilly.model.dto.Meme
import com.chsdngm.tilly.repository.ImageDao
import com.chsdngm.tilly.repository.MemeDao
import com.chsdngm.tilly.repository.DistributedModerationDao
import com.chsdngm.tilly.repository.PrivateModeratorRepository
import com.chsdngm.tilly.repository.UserRepository
import com.chsdngm.tilly.similarity.ImageMatcher
import com.chsdngm.tilly.similarity.ImageTextRecognizer
import com.chsdngm.tilly.utility.*
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
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


@Service
class MemeHandler(
    private val userRepository: UserRepository,
    private val imageMatcher: ImageMatcher,
    private val imageTextRecognizer: ImageTextRecognizer,
    private val imageDao: ImageDao,
    private val privateModeratorRepository: PrivateModeratorRepository,
    private val memeDao: MemeDao,
    private val distributedModerationDao: DistributedModerationDao
) : AbstractHandler<MemeUpdate> {

    var executor: ExecutorService = Executors.newSingleThreadExecutor()

    private val log = LoggerFactory.getLogger(javaClass)

    private val distributedModerationMessage = """
        Ты теперь в числе тех, кому доверено оценивать мемы. Дорожи этим и оценивай с душой.
    """.trimIndent()

    private val moderationPool = TreeMap<Int, WeightedModerationType>()
    private val moderationType = Random()
    private var totalWeight: Int = 0

    init {
        WeightedModerationType.values().forEach {
            totalWeight += it.weight
            moderationPool[totalWeight] = it
        }
    }

    fun handle(update: AutoSuggestedMemeUpdate): CompletableFuture<Void> = CompletableFuture.supplyAsync({
        update.file = download(update.fileId)

        val duplicateFileId = imageMatcher.tryFindDuplicate(update.file)
        if (duplicateFileId != null) {
            sendDuplicateToBeta(update.user.mention(), duplicateFileId = update.fileId, originalFileId = duplicateFileId)
            return@supplyAsync
        }

        val message = SendPhoto().apply {
            chatId = CHAT_ID
            photo = InputFile(update.fileId)
            caption = runCatching { resolveCaption(update) }.getOrNull()
            parseMode = ParseMode.HTML
            replyMarkup = createMarkup(emptyMap())
        }.let(api::execute)

        val meme = memeDao.insert(
            Meme(
                CHAT_ID.toLong(),
                message.messageId,
                update.user.id.toInt(),
                update.status,
                null,
                update.fileId,
                update.caption
            )
        )

        log.info("sent for moderation to group chat. meme=$meme")
        handleImage(update)

    }, executor
    ).thenAccept { log.info("processed meme update=$update") }

    override fun handle(update: MemeUpdate): CompletableFuture<Void> = CompletableFuture.supplyAsync({
        update.file = download(update.fileId)

        val memeSender = userRepository.findById(update.user.id.toInt()).let { sender ->
            if (sender.isEmpty) {
                update.isFreshman = true
            }

            userRepository.save(
                TelegramUser(
                    update.user.id.toInt(),
                    update.user.userName,
                    update.user.firstName,
                    update.user.lastName,
                    //TODO refactor to upsert
                    if (sender.isEmpty) UserStatus.DEFAULT else sender.get().status,
                    distributedModerationGroupId=sender.map { it.distributedModerationGroupId }.orElseGet { null }
                )
            )
        }

        if (memeSender.status == UserStatus.BANNED) {
            replyToBannedUser(update)
            sendBannedEventToBeta(update, memeSender)
            return@supplyAsync
        }

        val duplicateFileId = imageMatcher.tryFindDuplicate(update.file)
        if (duplicateFileId != null) {
            handleDuplicate(update, duplicateFileId)
            return@supplyAsync
        }

        if (update.isFreshman || update.status == LOCAL) {
            moderateWithGroup(update)
        } else {
            // Balancing with weight
            when (moderationPool.ceilingEntry(moderationType.nextInt(totalWeight)).value) {
                WeightedModerationType.PRIVATE -> tryPrivateModeration(update, memeSender) || moderateWithGroup(update)
                WeightedModerationType.DISTRIBUTED -> performDistributedModeration(update)
                WeightedModerationType.DEFAULT -> moderateWithGroup(update)
                else -> moderateWithGroup(update)
            }
        }

        handleImage(update)

        }, executor
    ).thenAccept { log.info("processed meme update=$update") }

    private fun handleImage(update: MemeUpdate) {
        val analyzingResults = imageTextRecognizer.analyzeAndIndex(update.file, update.fileId)
        val image = Image(
            update.fileId,
            update.file.readBytes(),
            hash = imageMatcher.calculateHash(update.file),
            rawText = analyzingResults?.words,
            rawLabels = analyzingResults?.labels
        )

        imageDao.insert(image)
        imageMatcher.add(image)
    }

    private fun performDistributedModeration(update: MemeUpdate): Boolean {
        fun getDistributedModerationGroupId(): Int = 1

        val distributedModerationGroupId = getDistributedModerationGroupId()

        val senderMessageId = replyToSender(update).messageId
        val meme = memeDao.insert(
                Meme(
                        null,
                        null,
                        update.user.id.toInt(),
                        update.status,
                        senderMessageId,
                        update.fileId,
                        update.caption
                )
        )
        val distributedGroupMembers = userRepository.findAllByDistributedModerationGroupId(distributedModerationGroupId)

        for (member in distributedGroupMembers) {
            SendPhoto().apply {
                chatId = member.id.toString()
                photo = InputFile(update.fileId)
                caption = (update.caption?.let { it + "\n\n" } ?: "") + distributedModerationMessage
                parseMode = ParseMode.HTML
                replyMarkup = createDistributedModerationMarkup()
            }.let { api.execute(it) }.let { sent ->
                distributedModerationDao.createEvent(meme.id, member.id.toLong(), sent.messageId, distributedModerationGroupId)
            }
        }

        return true
    }

    private fun tryPrivateModeration(update: MemeUpdate, sender: TelegramUser): Boolean {
        val currentModerators = privateModeratorRepository.findCurrentModeratorsIds()

        if (currentModerators.size >= 5) {
            return false
        }

        val moderationCandidates = userRepository.findTopSenders(sender.id, TelegramConfig.BOT_ID)
            .filter { potentialModerator -> !currentModerators.contains(potentialModerator.id) }

        if (moderationCandidates.isEmpty()) {
            return false
        }

        fun successfullyModerated(moderator: TelegramUser) = runCatching {
            log.info("Picked moderator=$moderator")

            moderateWithUser(update, moderator.id.toLong()).also { meme ->
                log.info("sent for moderation to user=$moderator. meme=$meme")
                privateModeratorRepository.addPrivateModerator(moderator.id)
                sendPrivateModerationEventToBeta(meme, sender, moderator)
            }
        }.onFailure {
            logExceptionInBetaChat(it)
        }.isSuccess

        for (moderator in moderationCandidates) {
            if (successfullyModerated(moderator)) {
                return true
            }
        }

        return false
    }

    private fun handleDuplicate(update: MemeUpdate, duplicateFileId: String) {
        sendSorryText(update)

        memeDao.findByFileId(duplicateFileId)?.also { meme ->
            if (meme.channelMessageId == null) {
                forwardMemeFromChatToUser(meme, update.user)
            } else {
                forwardMemeFromChannelToUser(meme, update.user)
            }
            log.info("successfully forwarded original meme to sender=${update.user.id}. $meme")
            sendDuplicateToBeta(update.user.mention(), duplicateFileId = update.fileId, originalFileId = meme.fileId)
        }
    }

    private fun moderateWithGroup(update: MemeUpdate): Boolean {
        SendPhoto().apply {
            chatId = CHAT_ID
            photo = InputFile(update.fileId)
            caption = runCatching { resolveCaption(update) }.getOrNull()
            parseMode = ParseMode.HTML
            replyMarkup = createMarkup(emptyMap())
        }.let(api::execute).let {

            val senderMessageId = replyToSender(update).messageId
            val meme = memeDao.insert(
                Meme(
                    CHAT_ID.toLong(),
                    it.messageId,
                    update.user.id.toInt(),
                    update.status,
                    senderMessageId,
                    update.fileId,
                    update.caption
                )
            )

            log.info("sent for moderation to group chat. meme=$meme")
            return true
        }
    }

    private fun moderateWithUser(update: MemeUpdate, moderatorId: Long): Meme =
        SendPhoto().apply {
            chatId = moderatorId.toString()
            photo = InputFile(update.fileId)
            caption = (update.caption?.let { it + "\n\n" } ?: "") + "Теперь ты модератор!"
            parseMode = ParseMode.HTML
            replyMarkup = createPrivateModerationMarkup()
        }.let { api.execute(it) }.let { sent ->
            val senderMessageId = replyToSenderAboutPrivateModeration(update).messageId
            memeDao.insert(
                Meme(
                    moderatorId,
                    sent.messageId,
                    update.user.id.toInt(),
                    update.status,
                    senderMessageId,
                    update.fileId,
                    update.caption
                )
            )
        }

    private fun resolveCaption(update: MemeUpdate): String {
        var caption = ""

        if (update.caption != null) {
            caption += update.caption
        }

        runCatching {
            GetChatMember().apply {
                chatId = CHAT_ID
                userId = update.user.id
            }.let(api::execute)
        }.onSuccess {

            if (!it.isFromChat() || it.isMemeManager()) {
                caption += "\n\nSender: ${it.user.mention()}"
            }
        }

        if (update.isFreshman) {
            caption += "\n\n#freshman"
        }

        return caption
    }

    private fun forwardMemeFromChannelToUser(meme: Meme, user: User) = ForwardMessage().apply {
        chatId = user.id.toString()
        fromChatId = CHANNEL_ID
        messageId = meme.channelMessageId!!
        disableNotification = true
    }.let { api.execute(it) }

    private fun forwardMemeFromChatToUser(meme: Meme, user: User) = ForwardMessage().apply {
        chatId = user.id.toString()
        fromChatId = meme.moderationChatId.toString()
        messageId = meme.moderationChatMessageId!!
        disableNotification = true
    }.let { api.execute(it) }

    private fun sendSorryText(update: MemeUpdate) = SendMessage().apply {
        chatId = update.user.id.toString()
        replyToMessageId = update.messageId
        disableNotification = true
        text = "К сожалению, мем уже был отправлен ранее!"
    }.let { api.execute(it) }

    private fun replyToSender(update: MemeUpdate): Message = SendMessage().apply {
        chatId = update.user.id.toString()
        replyToMessageId = update.messageId
        disableNotification = true
        text = "мем на модерации"
    }.let { api.execute(it) }

    private fun replyToSenderAboutPrivateModeration(update: MemeUpdate): Message = SendMessage().apply {
        chatId = update.user.id.toString()
        replyToMessageId = update.messageId
        disableNotification = true
        text = "мем на приватной модерации"
    }.let { api.execute(it) }

    private fun sendDuplicateToBeta(username: String, duplicateFileId: String, originalFileId: String) =
        SendMediaGroup().apply {
            chatId = BETA_CHAT_ID
            medias = listOf(InputMediaPhoto().apply {
                media = duplicateFileId
                caption = "дубликат, отправленный $username"
                parseMode = ParseMode.HTML
            }, InputMediaPhoto().apply {
                media = originalFileId
                caption = "оригинал"
            })
            disableNotification = true
        }.let { api.execute(it) }

    private fun sendPrivateModerationEventToBeta(
        meme: Meme,
        memeSender: TelegramUser,
        moderator: TelegramUser,
    ) = SendPhoto().apply {
        chatId = BETA_CHAT_ID
        photo = InputFile(meme.fileId)
        caption = "мем авторства ${memeSender.mention()} отправлен на личную модерацию к ${moderator.mention()}"
        parseMode = ParseMode.HTML
        disableNotification = true
    }.let { api.execute(it) }

    private fun download(fileId: String) =
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

    fun createDistributedModerationMarkup() = InlineKeyboardMarkup(
            listOf(
                    listOf(InlineKeyboardButton(APPROVE_DISTRIBUTED.emoji).also { it.callbackData = APPROVE_DISTRIBUTED.name }),
                    listOf(InlineKeyboardButton(DECLINE_DISTRIBUTED.emoji).also { it.callbackData = DECLINE_DISTRIBUTED.name })
            )
    )

    private fun replyToBannedUser(update: MemeUpdate): Message = SendMessage().apply {
        chatId = update.user.id.toString()
        replyToMessageId = update.messageId
        text = "Мем на привитой модерации"
    }.let { api.execute(it) }

    private fun sendBannedEventToBeta(update: MemeUpdate, telegramUser: TelegramUser) = SendPhoto().apply {
        chatId = BETA_CHAT_ID
        photo = InputFile(update.fileId)
        caption = "мем ${telegramUser.mention()} отправлен на личную модерацию в НИКУДА"
        parseMode = ParseMode.HTML
        disableNotification = true

    }.let { api.execute(it) }
}
