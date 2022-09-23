package com.chsdngm.tilly.utility


import com.chsdngm.tilly.config.TelegramConfig
import com.chsdngm.tilly.config.TelegramConfig.Companion.BETA_CHAT_ID
import com.chsdngm.tilly.config.TelegramConfig.Companion.BOT_ID
import com.chsdngm.tilly.config.TelegramConfig.Companion.MONTORN_CHAT_ID
import com.chsdngm.tilly.config.TelegramConfig.Companion.api
import com.chsdngm.tilly.format
import com.chsdngm.tilly.model.AutosuggestionVoteValue
import com.chsdngm.tilly.model.PrivateVoteValue
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.model.dto.Meme
import com.chsdngm.tilly.model.dto.Vote
import org.apache.commons.io.IOUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.MemberStatus
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import java.io.File
import java.io.FileOutputStream
import java.io.Serializable
import java.net.URL
import java.sql.ResultSet
import java.time.Instant
import java.util.concurrent.CompletableFuture

fun Update.hasMeme() = this.hasMessage() && this.message.chat.isUserChat && this.message.hasPhoto()

fun Update.hasCommand() = this.hasMessage() &&
        (this.message.chat.isUserChat || this.message.chatId.toString() == BETA_CHAT_ID) &&
        this.message.isCommand

fun Update.hasVote() = this.hasCallbackQuery()
        && (this.callbackQuery.message.isSuperGroupMessage || this.callbackQuery.message.isChannelMessage)
        && runCatching {
    setOf(*VoteValue.values()).map { it.name }.contains(this.callbackQuery.data)
}.getOrDefault(false)

fun Update.hasPrivateVote() = this.hasCallbackQuery()
        && this.callbackQuery.message.chat.isUserChat
        && runCatching {
    setOf(*PrivateVoteValue.values()).map { it.name }.contains(this.callbackQuery.data)
}.getOrDefault(false)

fun Update.hasAutosuggestionVote() = this.hasCallbackQuery()
        && this.callbackQuery.message.chatId.toString() == MONTORN_CHAT_ID
        && runCatching {
    setOf(*AutosuggestionVoteValue.values()).map { it.name }.contains(this.callbackQuery.data)
}.getOrDefault(false)

fun User.mention(): String =
    if (this.id == BOT_ID) "montorn"
    else """<a href="tg://user?id=${this.id}">${this.userName ?: this.firstName}</a>"""

fun ChatMember.isMemeManager() = this.user.isBot && this.user.id == BOT_ID

fun ChatMember.isFromChat(): Boolean = chatUserStatuses.contains(this.status)

private val chatUserStatuses = setOf(MemberStatus.ADMINISTRATOR, MemberStatus.CREATOR, MemberStatus.MEMBER)

fun createMarkup(votes: List<Vote>): InlineKeyboardMarkup {
    val stats = votes.groupingBy { it.value }.eachCount()

    return InlineKeyboardMarkup().apply {
        keyboard = listOf(
            listOf(
                createVoteInlineKeyboardButton(VoteValue.UP, stats.getOrDefault(VoteValue.UP, 0)),
                createVoteInlineKeyboardButton(VoteValue.DOWN, stats.getOrDefault(VoteValue.DOWN, 0))
            )
        )
    }
}

fun updateStatsInSenderChat(meme: Meme, votes: List<Vote>): CompletableFuture<Serializable?> =
    if (meme.privateReplyMessageId != null && meme.senderId != BOT_ID) {
        val caption = meme.status.description +
                votes
                    .groupingBy { it.value }
                    .eachCount().entries
                    .sortedBy { it.key }
                    .joinToString(
                        prefix = " статистика: \n\n",
                        transform = { (value, sum) -> "${value.emoji}: $sum" })

        EditMessageText().apply {
            chatId = meme.senderId.toString()
            messageId = meme.privateReplyMessageId
            text = caption
        }.let { api.executeAsync(it) }

    } else {
        CompletableFuture.completedFuture(null)
    }


fun logExceptionInBetaChat(ex: Throwable): Message =
    SendMessage().apply {
        chatId = BETA_CHAT_ID
        text = ex.format(update = null)
        parseMode = ParseMode.HTML
    }.let { method -> api.execute(method) }

private fun createVoteInlineKeyboardButton(voteValue: VoteValue, voteCount: Int) =
    InlineKeyboardButton().also {
        it.text = if (voteCount == 0) voteValue.emoji else voteValue.emoji + " " + voteCount
        it.callbackData = voteValue.name
    }

fun Instant.minusDays(days: Int): Instant = this.minusSeconds(days.toLong() * 24 * 60 * 60)

fun <T : Any> String.execAndMap(transform: (ResultSet) -> T): List<T> {
    val result = arrayListOf<T>()
    TransactionManager.current().exec(this) { rs ->
        while (rs.next()) {
            result += transform(rs)
        }
    }
    return result
}

fun LongArray.toSql() = this.joinToString(prefix = "(", postfix = ")")

fun download(fileId: String): File {
    val file = File.createTempFile("photo-", "-" + Thread.currentThread().id + "-" + System.currentTimeMillis())
    file.deleteOnExit()

    FileOutputStream(file).use { out ->
        URL(api.execute(GetFile(fileId)).getFileUrl(TelegramConfig.BOT_TOKEN)).openStream()
            .use { stream -> IOUtils.copy(stream, out) }
    }

    return file
}