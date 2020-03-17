package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.MemeEntity
import com.chsdngm.tilly.model.MemeStatsEntry
import com.chsdngm.tilly.model.VoteEntity
import com.chsdngm.tilly.model.VoteValue.*
import com.chsdngm.tilly.utility.SqlQueries
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class VoteRepository(private val template: NamedParameterJdbcTemplate, private val queries: SqlQueries) {
  fun insertOrUpdate(vote: VoteEntity): Int = template.update(queries.getFromConfOrFail("insertOrUpdateVote"), getParams(vote))

  fun delete(vote: VoteEntity) = template.update(queries.getFromConfOrFail("deleteVote"), getParams(vote))

  fun getStatsByUser(userId: Int): List<MemeStatsEntry> = template.query(
      queries.getFromConfOrFail("findUserStats"),
      MapSqlParameterSource("userId", userId))
  { rs, _ ->
    MemeStatsEntry(
        UP to rs.getInt(UP.name),
        DOWN to rs.getInt(DOWN.name))
  }.toList()

  fun getVotes(meme: MemeEntity): List<VoteEntity> = template.query(
      queries.getFromConfOrFail("findMemeVotes"),
      MapSqlParameterSource("chatMessageId", meme.chatMessageId))
  { rs, _ ->
    VoteEntity(
        rs.getInt("chat_message_id"),
        rs.getInt("voter_id"),
        valueOf(rs.getString("value")))
  }.toList()

  private fun getParams(vote: VoteEntity): MapSqlParameterSource =
      MapSqlParameterSource("chatMessageId", vote.messageId)
          .addValue("voterId", vote.voterId)
          .addValue("value", vote.voteValue.name)
}
