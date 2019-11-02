package com.imbananko.tilly.repository

import com.imbananko.tilly.model.MemeEntity
import com.imbananko.tilly.utility.SqlQueries
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.*

@Repository
class MemeRepository(private val template: NamedParameterJdbcTemplate, private val queries: SqlQueries) {
  private val memeSenderCache: WeakHashMap<Int, Int> = WeakHashMap()

  fun save(memeEntity: MemeEntity) {
    template.update(queries.getFromConfOrFail("insertMeme"),
        MapSqlParameterSource("chatId", memeEntity.chatId)
            .addValue("messageId", memeEntity.messageId)
            .addValue("senderId", memeEntity.senderId)
            .addValue("fileId", memeEntity.fileId))

    memeSenderCache[Objects.hash(memeEntity.chatId, memeEntity.messageId)] = memeEntity.senderId
  }

  fun getMemeSender(chatId: Long, messageId: Int): Int =
      memeSenderCache.computeIfAbsent(Objects.hash(chatId, messageId)) {
        template.queryForObject(
            queries.getFromConfOrFail("findMemeSender"),
            MapSqlParameterSource("chatId", chatId).addValue("messageId", messageId),
            Int::class.java
        )
      }

  fun load(chatId: Long): List<MemeEntity> =
      template.query(
          queries.getFromConfOrFail("loadMemes"),
          MapSqlParameterSource("chat_id", chatId)
      ) { rs: ResultSet, _: Int ->
        MemeEntity(rs.getLong("chat_id"),
            rs.getInt("message_id"),
            rs.getInt("sender_id"),
            rs.getString("file_id"))
      }

  fun messageIdByFileId(fileId: String, chatId: Long): Int? = template.query(queries.getFromConfOrFail("messageIdByFileId"),
      MapSqlParameterSource("chat_id", chatId).addValue("file_id", fileId)
  ) { rs, _ -> rs.getInt("message_id") }[0]
}
