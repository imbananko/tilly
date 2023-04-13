package com.chsdngm.tilly.repository

import com.chsdngm.tilly.config.Metadata.Companion.MODERATION_THRESHOLD
import com.chsdngm.tilly.model.MemeStatus
import com.chsdngm.tilly.model.dto.*
import com.chsdngm.tilly.utility.allColumns
import com.chsdngm.tilly.utility.execAndMap
import com.chsdngm.tilly.utility.indexedColumns
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class MemeDao(val database: Database) {

    fun findMemeByChannelMessageId(channelMessageId: Int): Pair<Meme, List<Vote>>? = transaction {
        (Memes leftJoin Votes)
            .select(Memes.channelMessageId eq channelMessageId)
            .toList()
            .toMemeWithVotes()
    }

    fun findMemeByModerationChatIdAndModerationChatMessageId(
        moderationChatId: Long,
        moderationChatMessageId: Int,
    ): Pair<Meme, List<Vote>>? = transaction {
        Memes.leftJoin(Votes)
            .select((Memes.moderationChatId eq moderationChatId) and (Memes.moderationChatMessageId eq moderationChatMessageId))
            .toList()
            .toMemeWithVotes()
    }

    fun insert(meme: Meme) = transaction {
        Memes.insert { meme.toInsertStatement(it) }.resultedValues?.first()?.toMeme()
            ?: throw NoSuchElementException("Error saving meme")
    }

    suspend fun update(meme: Meme) = newSuspendedTransaction {
        Memes.update({ Memes.id eq meme.id }) { meme.toUpdateStatement(it) }
    }

    suspend fun findAllByStatusOrderByCreated(memeStatus: MemeStatus): Map<Meme, List<Vote>> = newSuspendedTransaction {
        (Memes leftJoin Votes)
            .select { Memes.status eq memeStatus }.orderBy(Memes.created).toMemesWithVotes()
    }

    suspend fun findAllBySenderId(senderId: Long): Map<Meme, List<Vote>> = newSuspendedTransaction {
        (Memes leftJoin Votes)
            .select { Memes.senderId eq senderId }.toMemesWithVotes()
    }

    fun findByFileId(fileId: String): Meme? = transaction {
        Memes.select { Memes.fileId eq fileId }.singleOrNull()?.toMeme()
    }

    suspend fun findTopRatedMemeForLastWeek(): Meme? = newSuspendedTransaction {
        val sql = """
                select ${Memes.allColumns} 
                from meme    
                    left join vote v on id = v.meme_id where channel_message_id is not null    
                    and meme.created > current_timestamp - interval '7 days' 
                group by id 
                order by count(value) filter (where value = 'UP') - count(value) filter (where value = 'DOWN') desc 
                limit 1;
                """.trimIndent()

        sql.execAndMap { rs -> ResultRow.create(rs, Memes.indexedColumns) }.toMeme()
    }

    suspend fun saveMemeOfTheWeek(memeId: Int) = newSuspendedTransaction {
        val sql = """
            insert into meme_of_week (meme_id) 
            values ($memeId);
            """.trimIndent()

        sql.execAndMap { }
    }

    suspend fun findDeadMemes(): List<Meme> = newSuspendedTransaction {
        val ascOrDesc = if (LocalDate.now().dayOfYear % 2 == 0) "asc" else "desc"

        val sql = """
            select ${Memes.allColumns} 
            from (select meme.*,
                         count(vote) filter ( where vote.value = 'UP' )  as ups,
                         count(vote) filter ( where vote.value = 'DOWN') as downs
                  from meme
                           left join vote on meme.id = vote.meme_id
                  where meme.status = 'MODERATION'
                    and meme.created <= now() - interval '14 days'
                    and meme.caption is null
                  group by meme.id) as meme
            where (ups = 4 and downs = 0)
               OR (ups = 5 and downs = 1)
            order by meme.created $ascOrDesc
            limit 5;
        """.trimIndent()

        sql.execAndMap { rs -> ResultRow.create(rs, Memes.indexedColumns) }.toMemes()
    }

    fun scheduleMemes(): List<Meme> = transaction {
        val sql = """
                     update meme 
                     set status='SCHEDULED'
                     where id in (
                           select meme.id
                           from meme
                                    left join vote on meme.id = vote.meme_id
                           where meme.status = 'MODERATION'
                             and meme.channel_message_id is null
                             and meme.created > now() - interval '7 days'
                           group by meme.id
                           having count(vote) filter ( where vote.value = 'UP' ) -
                                  count(vote) filter ( where vote.value = 'DOWN') >= $MODERATION_THRESHOLD
                     ) returning ${Memes.allColumns};
                  """.trimIndent()

        sql.execAndMap({ rs -> ResultRow.create(rs, Memes.indexedColumns) }, StatementType.SELECT).toMemes()
    }
}


