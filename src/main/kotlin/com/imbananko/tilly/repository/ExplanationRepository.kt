package com.imbananko.tilly.repository

import com.imbananko.tilly.model.ExplanationEntity
import com.imbananko.tilly.utility.SqlQueries
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.ZoneId
import java.time.ZoneOffset

@Repository
class ExplanationRepository(private val template: NamedParameterJdbcTemplate, private val queries: SqlQueries) {
  fun save(explanationEntity: ExplanationEntity) {
    template.update(queries.getFromConfOrFail("addExplanation"),
        MapSqlParameterSource("userId", explanationEntity.userId)
            .addValue("chatId", explanationEntity.chatId)
            .addValue("messageId", explanationEntity.messageId)
            .addValue("explainReplyMessageId", explanationEntity.explainReplyMessageId)
            .addValue("explainTill", explanationEntity.explainTill.atZone(ZoneId.of("Europe/Moscow")).toLocalDateTime())
    )
  }

  fun listExpiredExplanations(): List<ExplanationEntity> {
    return template.query(queries.getFromConfOrFail("listExpiredExplanations")) { rs: ResultSet, _: Int ->
      ExplanationEntity(
          rs.getInt("user_id"),
          rs.getLong("chat_id"),
          rs.getInt("message_id"),
          rs.getInt("explain_reply_message_id"),
          rs.getTimestamp("explain_till").toLocalDateTime().toInstant(ZoneOffset.ofHours(3))
      )
    }
  }

  fun deleteExplanation(userId: Int, chatId: Long, explainReplyMessageId: Int) {
    template.update(queries.getFromConfOrFail("deleteExplanation"),
        MapSqlParameterSource("userId", userId)
            .addValue("chatId", chatId)
            .addValue("explainReplyMessageId", explainReplyMessageId)
    )
  }
}