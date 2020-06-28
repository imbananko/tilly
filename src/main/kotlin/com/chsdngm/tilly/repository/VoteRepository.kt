package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.*
import com.chsdngm.tilly.utility.SqlQueries
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class VoteRepository(private val template: NamedParameterJdbcTemplate, private val queries: SqlQueries) {
  fun insertOrUpdate(vote: VoteEntity): Int =
      template.update(queries.getFromConfOrFail("insertOrUpdateVote"),
          MapSqlParameterSource("chatMessageId", vote.messageId)
              .addValue("voterId", vote.voterId)
              .addValue("value", vote.voteValue.name)
              .addValue("source", vote.source.name))

  fun delete(vote: VoteEntity) = template.update(queries.getFromConfOrFail("deleteVote"),
      MapSqlParameterSource("chatMessageId", vote.messageId)
          .addValue("voterId", vote.voterId)
          .addValue("value", vote.voteValue.name))

  fun getStatsByUser(userId: Int): List<MemeStatsEntry> = template.query(
      queries.getFromConfOrFail("findUserStats"),
      MapSqlParameterSource("userId", userId))
  { rs, _ ->
    MemeStatsEntry(
        rs.getInt(VoteValue.UP.name),
        rs.getInt(VoteValue.DOWN.name),
        rs.getBoolean("is_published"))
  }.toList()

  fun getVotes(meme: MemeEntity): List<VoteEntity> = template.query(
      queries.getFromConfOrFail("findMemeVotes"),
      MapSqlParameterSource("chatMessageId", meme.chatMessageId))
  { rs, _ ->
    VoteEntity(
        rs.getInt("chat_message_id"),
        rs.getInt("voter_id"),
        VoteValue.valueOf(rs.getString("value")),
        SourceType.valueOf(rs.getString("source")))
  }.toList()

}
