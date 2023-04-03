package com.chsdngm.tilly.utility


import com.chsdngm.tilly.config.TelegramConfig
import com.chsdngm.tilly.config.TelegramConfig.Companion.BETA_CHAT_ID
import com.chsdngm.tilly.config.TelegramConfig.Companion.BOT_ID
import com.chsdngm.tilly.config.TelegramConfig.Companion.MONTORN_CHAT_ID
import com.chsdngm.tilly.config.TelegramConfig.Companion.api
import com.chsdngm.tilly.model.*
import com.chsdngm.tilly.model.dto.Meme
import com.chsdngm.tilly.model.dto.Vote
import org.apache.commons.io.IOUtils
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.StatementType
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
import java.lang.reflect.UndeclaredThrowableException
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

fun Update.hasDistributedModerationVote() = this.hasCallbackQuery()
        && this.callbackQuery.message.chat.isUserChat
        && runCatching {
    setOf(*DistributedModerationVoteValue.values()).map { it.name }.contains(this.callbackQuery.data)
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

fun <T : Any> String.execAndMap(transform: (ResultSet) -> T,
                                explicitStatementType: StatementType?): List<T> {
    val result = arrayListOf<T>()
    TransactionManager.current().exec(this, explicitStatementType = explicitStatementType) { rs ->
        while (rs.next()) {
            result += transform(rs)
        }
    }
    return result
}

fun <T : Any> String.execAndMap(transform: (ResultSet) -> T): List<T> {
    return this.execAndMap(transform, explicitStatementType = null)
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

val Table.allColumns get() = fields.joinToString(", ") { "$tableName.${(it as Column<*>).name}" }
val Table.indexedColumns get() = realFields.toSet().mapIndexed { index, expression -> expression to index }.toMap()

fun Throwable.format(update: Update?): String {
    val updateInfo = when {
        update == null -> "no update"
        update.hasVote() -> VoteUpdate(update).toString()
        update.hasMeme() -> UserMemeUpdate(update).toString()
        update.hasCommand() -> CommandUpdate(update).toString()
        update.hasPrivateVote() -> PrivateVoteUpdate(update).toString()
        else -> "unknown update=$update"
    }

    val exForBeta = when (this) {
        is UndeclaredThrowableException ->
            ExceptionForBeta(this.undeclaredThrowable.message, this, this.undeclaredThrowable.stackTrace)
        else ->
            ExceptionForBeta(this.message, this.cause, this.stackTrace)
    }

    return """
  |Exception: ${exForBeta.message}
  |
  |Cause: ${exForBeta.cause}
  |
  |Update: $updateInfo
  |
  |Stacktrace: 
  |${
        exForBeta.stackTrace.filter { it.className.contains("chsdngm") || it.className.contains("telegram") }
            .joinToString(separator = "\n\n") { "${it.className}.${it.methodName}:${it.lineNumber}" }
    }
  """.trimMargin()
}

private class ExceptionForBeta(
    val message: String?,
    val cause: Throwable?,
    val stackTrace: Array<StackTraceElement>
)