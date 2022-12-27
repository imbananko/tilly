package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.config.TelegramConfig
import com.chsdngm.tilly.config.TelegramConfig.Companion.BETA_CHAT_ID
import com.chsdngm.tilly.config.TelegramConfig.Companion.BOT_TOKEN
import com.chsdngm.tilly.config.TelegramConfig.Companion.CHANNEL_ID
import com.chsdngm.tilly.config.TelegramConfig.Companion.CHAT_ID
import com.chsdngm.tilly.config.TelegramConfig.Companion.LOGS_CHAT_ID
import com.chsdngm.tilly.config.TelegramConfig.Companion.api
import com.chsdngm.tilly.metrics.MetricsUtils
import com.chsdngm.tilly.model.AutoSuggestedMemeUpdate
import com.chsdngm.tilly.model.MemeStatus.LOCAL
import com.chsdngm.tilly.model.MemeUpdate
import com.chsdngm.tilly.model.PrivateVoteValue.APPROVE
import com.chsdngm.tilly.model.PrivateVoteValue.DECLINE
import com.chsdngm.tilly.model.DistributedModerationVoteValue.APPROVE_DISTRIBUTED
import com.chsdngm.tilly.model.DistributedModerationVoteValue.DECLINE_DISTRIBUTED
import com.chsdngm.tilly.model.UserStatus
import com.chsdngm.tilly.model.WeightedModerationType
import com.chsdngm.tilly.model.dto.DistributedModerationEvent
import com.chsdngm.tilly.model.dto.Image
import com.chsdngm.tilly.model.dto.Meme
import com.chsdngm.tilly.model.dto.TelegramUser
import com.chsdngm.tilly.repository.DistributedModerationEventDao
import com.chsdngm.tilly.repository.ImageDao
import com.chsdngm.tilly.repository.MemeDao
import com.chsdngm.tilly.repository.TelegramUserDao
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
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Function
import kotlin.math.abs


@Service
class MemeHandler(
        private val telegramUserDao: TelegramUserDao,
        private val imageMatcher: ImageMatcher,
        private val imageTextRecognizer: ImageTextRecognizer,
        private val imageDao: ImageDao,
        private val memeDao: MemeDao,
        private val distributedModerationEventDao: DistributedModerationEventDao,
        private val metricsUtils: MetricsUtils) : AbstractHandler<MemeUpdate>() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val memeExecutorService = Executors.newSingleThreadExecutor()
    private val distributedModerationExecutor = Executors.newFixedThreadPool(10)

    private val moderationRages = mutableListOf<Int>()
    private val random = Random()
    private var totalWeight: Int = 0

    init {
        WeightedModerationType.values().forEach {
            moderationRages.add(totalWeight + it.weight)
            totalWeight += it.weight
        }
    }

    fun handle(update: AutoSuggestedMemeUpdate) {
        val duplicateFileId = imageMatcher.tryFindDuplicate(update.file)
        if (duplicateFileId != null) {
            sendDuplicateToLog(
                update.user.mention(),
                duplicateFileId = update.fileId,
                originalFileId = duplicateFileId
            )
            return
        }

        val message = SendPhoto().apply {
            chatId = CHAT_ID
            photo = InputFile(update.fileId)
            caption = runCatching { resolveCaption(update) }.getOrNull()
            parseMode = ParseMode.HTML
            replyMarkup = createMarkup(listOf())
        }.let(api::execute)

        val meme = memeDao.insert(
            Meme(
                CHAT_ID.toLong(),
                message.messageId,
                update.user.id,
                update.status,
                null,
                update.fileId,
                update.caption
            )
        )

        log.info("sent for moderation to group chat. meme=$meme")
        handleImage(update)

        log.info("processed meme update=$update")
    }

    override fun measureTime(update: MemeUpdate) {
        metricsUtils.measure(update)
    }

    override fun handleSync(update: MemeUpdate) {
        val foundUser = telegramUserDao.findById(update.user.id)
        val memeSender = if (foundUser == null) {
            update.isFreshman = true

            telegramUserDao.insert(TelegramUser(
                update.user.id,
                update.user.userName,
                update.user.firstName,
                update.user.lastName
            ))
        } else {
            val updatedUser = foundUser.copy(
                username = update.user.userName,
                firstName = update.user.firstName,
                lastName = update.user.lastName
            )

            if (foundUser != updatedUser) {
                telegramUserDao.update(updatedUser)
            }

            updatedUser
        }

        if (memeSender.status == UserStatus.BANNED) {
            replyToBannedUser(update)
            sendBannedEventToLog(update, memeSender)
            return
        }

        val duplicateFileId = imageMatcher.tryFindDuplicate(update.file)
        if (duplicateFileId != null) {
            handleDuplicate(update, duplicateFileId)
            return
        }

        if (update.isFreshman || update.status == LOCAL) {
            moderateWithGroup(update)
        } else {
            // Balancing with weight
            var index = moderationRages.binarySearch(random.nextInt(totalWeight))

            if (index < 0) {
                index = abs(index + 1)
            }

            when (WeightedModerationType.values()[index]) {
                WeightedModerationType.PRIVATE -> tryPrivateModeration(update, memeSender) || moderateWithGroup(update)
                WeightedModerationType.DISTRIBUTED -> performDistributedModeration(update, memeSender) || moderateWithGroup(update)
                WeightedModerationType.DEFAULT -> moderateWithGroup(update)
            }
        }

        handleImage(update)
    }

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

    private fun performDistributedModeration(update: MemeUpdate, sender: TelegramUser): Boolean = runCatching {
        fun getDistributedModerationGroupId(): Int = 1

        val distributedModerationGroupId = getDistributedModerationGroupId()
        val distributedGroupMembers = telegramUserDao.findAllByDistributedModerationGroupId(distributedModerationGroupId)

        if (distributedGroupMembers.any { it.id == update.user.id }) {
            return false
        }

        val senderMessageId = replyToSender(update).messageId
        val meme = memeDao.insert(
                Meme(
                        null,
                        null,
                        update.user.id,
                        update.status,
                        senderMessageId,
                        update.fileId,
                        update.caption
                )
        )

        val futures = distributedGroupMembers.map { member ->
            sendMemeToDistributedModerator(SendPhoto().apply {
                chatId = member.id.toString()
                photo = InputFile(update.fileId)
                caption = update.caption ?: ""
                replyMarkup = createDistributedModerationMarkup()
            }).thenAccept { sent ->
                if (sent != null) distributedModerationEventDao.insert(
                    DistributedModerationEvent(meme.id, member.id, sent.messageId, distributedModerationGroupId)
                )
            }
        }

        CompletableFuture.allOf(*futures.toTypedArray())

        log.info("meme $meme was sent to distributed moderation group members: ${distributedGroupMembers.map { it.username }}")
        sendDistributedModerationEventToLog(meme, sender, distributedGroupMembers)
    }.isSuccess

    private fun tryPrivateModeration(update: MemeUpdate, sender: TelegramUser): Boolean {
        val currentModerators = telegramUserDao.findUsersWithRecentlyPrivateModerationAssignment()

        if (currentModerators.size >= 5) {
            return false
        }

        val moderationCandidates = telegramUserDao.findTopFiveSendersForLastWeek(
            sender.id,
            TelegramConfig.BOT_ID,
            *currentModerators.map { it.id }.toLongArray())

        if (moderationCandidates.isEmpty()) {
            return false
        }

        fun successfullyModerated(moderator: TelegramUser) = runCatching {
            log.info("Picked moderator=$moderator")

            moderateWithUser(update, moderator.id).also { meme ->
                log.info("sent for moderation to user=$moderator. meme=$meme")
                telegramUserDao.update(moderator.apply { privateModerationLastAssignment = Instant.now() })
                sendPrivateModerationEventToLog(meme, sender, moderator)
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
            if (meme.moderationChatId != null) {
                if (meme.channelMessageId == null) {
                    forwardMemeFromChatToUser(meme, update.user)
                } else {
                    forwardMemeFromChannelToUser(meme, update.user)
                }

                log.info("successfully forwarded original meme to sender=${update.user.id}. $meme")

            }

            sendDuplicateToLog(update.user.mention(), duplicateFileId = update.fileId, originalFileId = meme.fileId)
        }
    }

    private fun moderateWithGroup(update: MemeUpdate): Boolean {
        SendPhoto().apply {
            chatId = CHAT_ID
            photo = InputFile(update.fileId)
            caption = runCatching { resolveCaption(update) }.getOrNull()
            parseMode = ParseMode.HTML
            replyMarkup = createMarkup(listOf())
        }.let(api::execute).let {

            val senderMessageId = replyToSender(update).messageId
            val meme = memeDao.insert(
                Meme(
                    CHAT_ID.toLong(),
                    it.messageId,
                    update.user.id,
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
                    update.user.id,
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

    private fun sendDuplicateToLog(username: String, duplicateFileId: String, originalFileId: String) =
        SendMediaGroup().apply {
            chatId = LOGS_CHAT_ID
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

    private fun sendPrivateModerationEventToLog(
        meme: Meme,
        memeSender: TelegramUser,
        moderator: TelegramUser,
    ) = SendPhoto().apply {
        chatId = LOGS_CHAT_ID
        photo = InputFile(meme.fileId)
        caption = "мем авторства ${memeSender.mention()} отправлен на личную модерацию к ${moderator.mention()}"
        parseMode = ParseMode.HTML
        disableNotification = true
    }.let { api.execute(it) }

    fun createPrivateModerationMarkup() = InlineKeyboardMarkup(
        listOf(
            listOf(InlineKeyboardButton("Отправить на канал ${APPROVE.emoji}").also { it.callbackData = APPROVE.name }),
            listOf(InlineKeyboardButton("Предать забвению ${DECLINE.emoji}").also { it.callbackData = DECLINE.name })
        )
    )

    fun createDistributedModerationMarkup() = InlineKeyboardMarkup(
            listOf(listOf(
                    InlineKeyboardButton(APPROVE_DISTRIBUTED.emoji).also { it.callbackData = APPROVE_DISTRIBUTED.name },
                    InlineKeyboardButton(DECLINE_DISTRIBUTED.emoji).also { it.callbackData = DECLINE_DISTRIBUTED.name })
            )
    )

    private fun replyToBannedUser(update: MemeUpdate): Message = SendMessage().apply {
        chatId = update.user.id.toString()
        replyToMessageId = update.messageId
        text = "Мем на привитой модерации"
    }.let { api.execute(it) }

    private fun sendBannedEventToLog(update: MemeUpdate, telegramUser: TelegramUser) =
        SendPhoto().apply {
            chatId = LOGS_CHAT_ID
            photo = InputFile(update.fileId)
            caption = "мем ${telegramUser.mention()} отправлен на личную модерацию в НИКУДА"
            parseMode = ParseMode.HTML
            disableNotification = true

        }.let { api.execute(it) }

    override fun getExecutor(): ExecutorService = memeExecutorService

    private fun sendMemeToDistributedModerator(memeMessage: SendPhoto,
                                               attemptNum: Int = 1,
                                               executor: Executor = distributedModerationExecutor): CompletableFuture<Message?> =
            if (attemptNum > 3) CompletableFuture.completedFuture(null)
            else CompletableFuture.supplyAsync({ api.execute(memeMessage) }, executor)
                    .thenApply { CompletableFuture.completedFuture(it) }
                    .exceptionally { ex ->
                        logExceptionInBetaChat(ex)
                        val delayedExecutor = CompletableFuture.delayedExecutor(5L * attemptNum, TimeUnit.SECONDS)
                        sendMemeToDistributedModerator(memeMessage, attemptNum + 1, delayedExecutor)
                    }
                    .thenCompose(Function.identity())

    private fun sendDistributedModerationEventToLog(meme: Meme,
                                                    memeSender: TelegramUser,
                                                    moderators: List<TelegramUser>) = SendPhoto().apply {
        chatId = LOGS_CHAT_ID
        photo = InputFile(meme.fileId)
        caption = "мем авторства ${memeSender.mention()} отправлен на распределенную модерацию к ${moderators.map { it.mention() }}"
        parseMode = ParseMode.HTML
        disableNotification = true
    }.let { api.execute(it) }
}
