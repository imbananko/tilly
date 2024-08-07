package com.chsdngm.tilly.repository

import com.chsdngm.tilly.*
import com.chsdngm.tilly.model.dto.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Repository
import java.sql.SQLException
import java.time.Instant

@Repository
class TelegramUserDao(val database: Database) {
    fun findById(id: Long): TelegramUser? = transaction {
        TelegramUsers.select(TelegramUsers.id eq id).singleOrNull()?.toTelegramUser()
    }

    fun insert(telegramUser: TelegramUser): TelegramUser = transaction {
        TelegramUsers.insert { telegramUser.toInsertStatement(it) }.resultedValues?.first()?.toTelegramUser()
            ?: throw SQLException("Error saving telegramUser")
    }

    fun update(telegramUser: TelegramUser) = transaction {
        TelegramUsers.update({ TelegramUsers.id eq telegramUser.id }) { telegramUser.toUpdateStatement(it) }
    }

    suspend fun findTopFiveSendersForLastWeek(vararg idsToExclude: Long): List<TelegramUser> = newSuspendedTransaction {
        val limit = 5
        val days = 7
        val sql = """
                select ${TelegramUsers.allColumns} 
                from meme m
                         inner join (select meme_id,
                                            count(*) filter (where value = 'UP')   up,
                                            count(*) filter (where value = 'DOWN') down,
                                            count(1)                               all_votes
                                     from vote
                                     where created >= now() - interval '$days days'
                                     group by meme_id) v
                                    on m.id = v.meme_id
                         inner join telegram_user on m.sender_id = telegram_user.id
                where m.channel_message_id is not null
                  and created >= now() - interval '$days days'
                  and telegram_user.id not in ${idsToExclude.toSql()}
                group by telegram_user.id
                order by sum(up) - sum(down) - 2 * count(1) desc
                limit $limit;
                """

        sql.execAndMap { rs -> ResultRow.create(rs, TelegramUsers.indexedColumns) }.toTelegramUsers()
    }

    suspend fun findUserRank(userId: String): Long? = newSuspendedTransaction {
        val sql = """
            select rank
            from (select m.sender_id,
                         row_number() over (
                             order by count(v) filter ( where v.value = 'UP' ) - count(v) filter ( where v.value = 'DOWN' ) -
                                      2 * count(distinct m.id) desc ) as rank
                  from meme m
                           left join vote v
                                     on m.id = v.meme_id
                  group by m.sender_id) as data
            where sender_id = $userId
        """.trimIndent()

        sql.execAndMap { rs -> rs.getLong(1) }.singleOrNull()
    }

    suspend fun findUserRank(userId: String, daysToPresentCount: Int): Long? = newSuspendedTransaction {
        val sql = """
            select rank
            from (select m.sender_id,
                         row_number() over (
                             order by count(v) filter ( where v.value = 'UP' ) - count(v) filter ( where v.value = 'DOWN' ) -
                                      2 * count(distinct m.id) desc ) as rank
                  from meme m
                           left join vote v
                                     on m.id = v.meme_id
                  where m.created >= now() - interval '$daysToPresentCount days' and v.created >= now() - interval '$daysToPresentCount days'
                  group by m.sender_id) as data
            where sender_id = $userId
        """.trimIndent()

        sql.execAndMap { rs -> rs.getLong(1) }.singleOrNull()
    }

    suspend fun findUsersWithRecentlyPrivateModerationAssignment(): List<TelegramUser> = newSuspendedTransaction {
        TelegramUsers
            .select { TelegramUsers.privateModerationLastAssignment greater Instant.now().minusDays(1) }
            .toTelegramUsers()
    }

    suspend fun findAllByDistributedModerationGroupId(distributedModerationGroupId: Int): List<TelegramUser> = newSuspendedTransaction {
        TelegramUsers
                .select { TelegramUsers.distributedModerationGroupId eq distributedModerationGroupId}
                .toTelegramUsers()
    }
}
