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
import com.imbananko.tilly.utility.downloadFromFileId
import com.imbananko.tilly.utility.extractVoteValue
import com.imbananko.tilly.utility.hasPhoto
import com.imbananko.tilly.utility.hasVote
import com.imbananko.tilly.utility.isP2PChat
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.io.IOException
import javax.annotation.PostConstruct

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
    fun init() {
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

    override fun getBotToken(): String? = token

    override fun getBotUsername(): String? = username

    override fun onUpdateReceived(update: Update): Unit {
        if (update.isP2PChat() && update.hasPhoto()) processMeme(update)
        if (update.hasVote()) processVote(update)
    }


    private fun processMeme(update: Update) {
        val message = update.message
        val fileId = message.photo[0].fileId
        val authorUsername = message.from.userName
        val mention = "[${authorUsername
            ?: message.from.firstName
            ?: message.from.lastName
            ?: "мутный тип"}](tg://user?id=${message.from.id})"

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
                log.error("Failed to send meme from message=" + message + ". Exception=" + throwable.cause)
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
                log.error("Failed to reply with existing meme from message=" + message + ". Exception=" + throwable.cause)
            }
        }

        runCatching { downloadFromFileId(fileId) }
            .mapCatching { memeFile -> memeMatcher.checkMemeExists(fileId, memeFile).getOrThrow()!! }
            .mapCatching { memeId -> processMemeIfExists(memeId).getOrThrow() }
            .onFailure { throwable: Throwable ->
                log.error("Failed to check if meme is unique, sending anyway. Exception={}", throwable.message)
                processMemeIfUnique()
            }
    }

    private fun processVote(update: Update) {
        val message = update.callbackQuery.message
        val targetChatId = message.chatId
        val messageId = message.messageId
        val vote = update.extractVoteValue()
        val voteSender = update.callbackQuery.from
        val memeSenderFromCaption = message.caption.split("Sender: ".toRegex()).dropLastWhile { it.isEmpty() }[1]

        val wasExplained = message
                .replyMarkup
                .keyboard[0][1]
                .callbackData
                .contains("EXPLAINED")

        val voteEntity = VoteEntity(targetChatId, messageId, voteSender.id, vote)

        if (voteSender.userName == memeSenderFromCaption ||
                memeRepository.getMemeSender(targetChatId, messageId) == voteSender.id) return

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

            val replyText = update.callbackQuery.message.caption.replaceFirst("Sender: ".toRegex(), "@") + ", поясни за мем"

            runCatching {
                execute<Message, SendMessage>(
                        SendMessage()
                            .setChatId(targetChatId)
                            .setReplyToMessageId(update.callbackQuery.message.messageId)
                            .setText(replyText)
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
}
