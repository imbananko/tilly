package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.MemeStatus
import com.chsdngm.tilly.model.dto.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Repository
import java.sql.ResultSet

@Repository
class MemeDao {
    val Memes.allFields get() = fields.joinToString(", ") { "$tableName.${(it as Column<*>).name}" }
    val indexedFields = Memes.realFields.toSet().mapIndexed { index, expression -> expression to index }.toMap()

    fun findMemeByChannelMessageId(channelMessageId: Int): Meme? = transaction {
        (Memes leftJoin Votes)
            .select(Memes.channelMessageId eq channelMessageId)
            .toList()
            .toMeme()
    }

    fun findMemeByModerationChatIdAndModerationChatMessageId(
        moderationChatId: Long,
        moderationChatMessageId: Int,
    ): Meme? = transaction {
        Memes.leftJoin(Votes)
            .select((Memes.moderationChatId eq moderationChatId) and (Memes.moderationChatMessageId eq moderationChatMessageId))
            .toList()
            .toMeme()
    }

    fun insert(meme: Meme) = transaction {
        Memes.insert { meme.toInsertStatement(it) }.resultedValues?.first()?.toMeme()
            ?: throw NoSuchElementException("Error saving meme")
    }

    fun update(meme: Meme) = transaction {
        Memes.update({ Memes.id eq meme.id }) { meme.toUpdateStatement(it) }
    }

    fun findAllByStatusOrderByCreated(memeStatus: MemeStatus): List<Meme> = transaction {
        (Memes leftJoin Votes)
            .select { Memes.status eq memeStatus }.orderBy(Memes.created).toMemes()
    }

    fun findAllBySenderId(senderId: Int): List<Meme> = transaction {
        (Memes leftJoin Votes)
            .select { Memes.senderId eq senderId }.toMemes()
    }

    fun findByFileId(fileId: String): Meme? = transaction {
        Memes.select { Memes.fileId eq fileId }.orderBy(Memes.created).singleOrNull()?.toMeme()
    }

    fun findTopRatedMemeForLastWeek(): Meme? = transaction {
        val sql = "" +
                "select ${Memes.allFields} " +
                "from meme " +
                "   left join vote v on id = v.meme_id " +
                "where channel_message_id is not null " +
                "   and meme.created > current_timestamp - interval '7 days' " +
                "group by id " +
                "order by count(value) filter (where value = 'UP') - count(value) filter (where value = 'DOWN') desc " +
                "limit 1;"

        sql.execAndMap { rs -> ResultRow.create(rs, indexedFields) }.toMeme()
    }

    fun saveMemeOfTheWeek(memeId: Int) = transaction {
        val sql = "" +
                "insert into meme_of_week (meme_id) " +
                "values ($memeId);"

        sql.execAndMap { }
    }

    fun <T : Any> String.execAndMap(transform: (ResultSet) -> T): List<T> {
        val result = arrayListOf<T>()
        TransactionManager.current().exec(this) { rs ->
            while (rs.next()) {
                result += transform(rs)
            }
        }
        return result
    }
}


