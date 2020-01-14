package com.imbananko.tilly.repository

import com.imbananko.tilly.model.MemeEntity
import com.imbananko.tilly.model.MemeStatsEntry
import com.imbananko.tilly.model.VoteEntity
import com.imbananko.tilly.model.VoteValue.*
import com.imbananko.tilly.utility.SqlQueries
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class VoteRepository(private val template: NamedParameterJdbcTemplate, private val queries: SqlQueries) {
  fun insertOrUpdate(vote: VoteEntity): Int = template.update(queries.getFromConfOrFail("insertOrUpdateVote"), getParams(vote))

  fun delete(vote: VoteEntity) = template.update(queries.getFromConfOrFail("deleteVote"), getParams(vote))

  fun getStatsByUser(chatId: Long, userId: Int): List<MemeStatsEntry> = template.query(
      queries.getFromConfOrFail("findUserStats"),
      MapSqlParameterSource("chatId", chatId).addValue("userId", userId))
  { rs, _ ->
    MemeStatsEntry(
        UP to rs.getInt(UP.name),
        DOWN to rs.getInt(DOWN.name))
  }.toList()

  fun getVotes(meme: MemeEntity): List<VoteEntity> = template.query(
      queries.getFromConfOrFail("findMemeVotes"),
      MapSqlParameterSource("chatId", meme.chatId).addValue("messageId", meme.messageId))
  { rs, _ ->
    VoteEntity(
        rs.getLong("chat_id"),
        rs.getInt("message_id"),
        rs.getInt("voter_id"),
        valueOf(rs.getString("value")))
  }.toList()

  private fun getParams(vote: VoteEntity): MapSqlParameterSource =
      MapSqlParameterSource("chatId", vote.chatId)
          .addValue("messageId", vote.messageId)
          .addValue("voterId", vote.voterId)
          .addValue("value", vote.voteValue.name)
}
