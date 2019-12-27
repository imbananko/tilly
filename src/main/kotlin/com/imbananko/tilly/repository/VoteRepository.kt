package com.imbananko.tilly.repository

import com.imbananko.tilly.model.StatsEntry
import com.imbananko.tilly.model.VoteEntity
import com.imbananko.tilly.model.VoteValue
import com.imbananko.tilly.model.VoteValue.*
import com.imbananko.tilly.utility.SqlQueries
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

  fun getStatsByMeme(chatId: Long, messageId: Int): Map<VoteValue, Int> =
      template.query(
          queries.getFromConfOrFail("findMemeStats"),
          MapSqlParameterSource("chatId", chatId).addValue("messageId", messageId)
      ) { rs, _ -> valueOf(rs.getString("value")) to rs.getLong("count").toInt() }.toMap()

  fun getStatsByUser(chatId: Long, userId: Int): List<StatsEntry> =
      template.query(
          queries.getFromConfOrFail("findUserStats"),
          MapSqlParameterSource("chatId", chatId).addValue("userId", userId)
      ) { rs, _ ->
        StatsEntry(
            UP to rs.getInt(UP.name),
            EXPLAIN to rs.getInt(EXPLAIN.name),
            DOWN to rs.getInt(DOWN.name))
      }.toList()

  private fun getParams(vote: VoteEntity): MapSqlParameterSource =
      MapSqlParameterSource("chatId", vote.chatId)
          .addValue("messageId", vote.messageId)
          .addValue("voterId", vote.voterId)
          .addValue("value", vote.voteValue.name)
}