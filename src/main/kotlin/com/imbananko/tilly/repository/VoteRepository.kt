package com.imbananko.tilly.repository

import com.imbananko.tilly.model.VoteEntity
import com.imbananko.tilly.model.VoteValue
import com.imbananko.tilly.utility.SqlQueries
import com.imbananko.tilly.utility.getFromConfOrFail
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class VoteRepository(private val template: NamedParameterJdbcTemplate, private val queries: SqlQueries) {
    fun exists(vote: VoteEntity): Boolean =
        template.queryForObject(queries.getFromConfOrFail("voteExists"), getParams(vote), Boolean::class.java) ?: false

    fun insertOrUpdate(vote: VoteEntity): Int = template.update(queries.getFromConfOrFail("insertOrUpdateVote"), getParams(vote))

    fun delete(vote: VoteEntity): Unit {
        template.update(queries.getFromConfOrFail("deleteVote"), getParams(vote))
    }

    fun getStats(chatId: Long, messageId: Int): Map<VoteValue, Int> =
            template.query(
                    queries.getFromConfOrFail("findVoteStats"),
                    MapSqlParameterSource("chatId", chatId).addValue("messageId", messageId)
            ) { rs, _ -> VoteValue.valueOf(rs.getString("value")) to rs.getLong("count").toInt() }.toMap()


    private fun getParams(vote: VoteEntity): MapSqlParameterSource =
        MapSqlParameterSource("chatId", vote.chatId)
                .addValue("messageId", vote.messageId)
                .addValue("voterId", vote.voterId)
                .addValue("value", vote.voteValue.name)
}