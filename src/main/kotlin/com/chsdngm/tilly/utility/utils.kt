package com.chsdngm.tilly.utility


import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.model.dto.Vote
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import java.lang.reflect.UndeclaredThrowableException
import java.sql.ResultSet
import java.time.Instant

fun User.mention(montornId: Long): String =
    if (this.id == montornId) "montorn"
    else """<a href="tg://user?id=${this.id}">${this.userName ?: this.firstName}</a>"""

fun User.mention(): String = """<a href="tg://user?id=${this.id}">${this.userName ?: this.firstName}</a>"""

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

val Table.allColumns get() = fields.joinToString(", ") { "$tableName.${(it as Column<*>).name}" }
val Table.indexedColumns get() = realFields.toSet().mapIndexed { index, expression -> expression to index }.toMap()

fun Throwable.format(): String {
    val formatted = formatExceptionForTelegramMessage(this)

    return """
          |Exception: ${formatted.message}
          |
          |Cause: ${formatted.cause}
          |
          |Update: null
          |
          |Stacktrace: 
          |${formatted.stackTrace}
  """.trimMargin()
}

fun formatExceptionForTelegramMessage(e: Throwable) = when (e) {
    is UndeclaredThrowableException ->
        TelegramFormattedException(e.undeclaredThrowable.message, e, filterAndFormat(e.undeclaredThrowable.stackTrace))
    else ->
        TelegramFormattedException(e.message, e.cause, filterAndFormat(e.stackTrace))
}

private fun filterAndFormat(stackTrace: Array<StackTraceElement>): String {
    return stackTrace.filter { it.className.contains("chsdngm") || it.className.contains("telegram") }
        .joinToString(separator = "\n") { "${it.className}.${it.methodName}:${it.lineNumber}" }
}

class TelegramFormattedException(
    val message: String?,
    val cause: Throwable?,
    val stackTrace: String
)
