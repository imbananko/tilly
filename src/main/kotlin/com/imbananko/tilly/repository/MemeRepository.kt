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

  fun save(meme: MemeEntity): MemeEntity {
    template.update(queries.getFromConfOrFail("insertMeme"),
        MapSqlParameterSource("chatId", meme.chatId)
            .addValue("messageId", meme.messageId)
            .addValue("senderId", meme.senderId)
            .addValue("fileId", meme.fileId)
            .addValue("privateMessageId", meme.privateMessageId))

    memeSenderCache[Objects.hash(meme.chatId, meme.messageId)] = meme.senderId
    return meme
  }

  fun findMemeByChat(chatId: Long, messageId: Int): MemeEntity? =
      template.queryForObject(queries.getFromConfOrFail("findMemeByChat"),
          MapSqlParameterSource("chatId", chatId).addValue("messageId", messageId))
      { rs, _ ->
        MemeEntity(rs.getLong("chat_id"),
            rs.getInt("message_id"),
            rs.getInt("sender_id"),
            rs.getString("file_id"),
            checkZero(rs.getInt("private_message_id")),
            checkZero(rs.getLong("channel_id")),
            checkZero(rs.getInt("channel_message_id")))
      }

  fun findMemeByChannel(channelId: Long, channelMessageId: Int): MemeEntity? =
      template.queryForObject(queries.getFromConfOrFail("findMemeByChannel"),
          MapSqlParameterSource("channelId", channelId).addValue("channelMessageId", channelMessageId))
      { rs, _ ->
        MemeEntity(rs.getLong("chat_id"),
            rs.getInt("message_id"),
            rs.getInt("sender_id"),
            rs.getString("file_id"),
            checkZero(rs.getInt("private_message_id")),
            checkZero(rs.getLong("channel_id")),
            checkZero(rs.getInt("channel_message_id")))
      }

  fun findMeme(filedId: String): MemeEntity? =
      template.queryForObject(queries.getFromConfOrFail("findMemeByFileId"),
          MapSqlParameterSource("fileId", filedId))
      { rs, _ ->
        MemeEntity(rs.getLong("chat_id"),
            rs.getInt("message_id"),
            rs.getInt("sender_id"),
            rs.getString("file_id"),
            checkZero(rs.getInt("private_message_id")),
            checkZero(rs.getLong("channel_id")),
            checkZero(rs.getInt("channel_message_id")))
      }

  fun findAllChatMemes(chatId: Long): List<MemeEntity> =
      template.query(
          queries.getFromConfOrFail("findAllChatMemes"),
          MapSqlParameterSource("chatId", chatId)
      ) { rs: ResultSet, _: Int ->
        MemeEntity(rs.getLong("chat_id"),
            rs.getInt("message_id"),
            rs.getInt("sender_id"),
            rs.getString("file_id"),
            checkZero(rs.getInt("private_message_id")),
            checkZero(rs.getLong("channel_id")),
            checkZero(rs.getInt("channel_message_id")))
      }

  fun findMemeOfTheWeek(channelId: Long): MemeEntity? =
      template.queryForObject(
          queries.getFromConfOrFail("getMemeOfTheWeek"),
          MapSqlParameterSource("channelId", channelId)
      ) { rs: ResultSet, _: Int ->
        MemeEntity(rs.getLong("chat_id"),
            rs.getInt("message_id"),
            rs.getInt("sender_id"),
            rs.getString("file_id"),
            checkZero(rs.getInt("private_message_id")),
            checkZero(rs.getLong("channel_id")),
            checkZero(rs.getInt("channel_message_id")))
      }

  fun findMemesOfTheYear(chatId: Long): List<MemeEntity> =
      template.query(
          queries.getFromConfOrFail("getMemesOfTheYear"),
          MapSqlParameterSource("chatId", chatId)
      ) { rs: ResultSet, _: Int ->
        MemeEntity(rs.getLong("chat_id"),
            rs.getInt("message_id"),
            rs.getInt("sender_id"),
            rs.getString("file_id"),
            checkZero(rs.getInt("private_message_id")),
            checkZero(rs.getLong("channel_id")),
            checkZero(rs.getInt("channel_message_id")))
      }.toList()

  fun update(old: MemeEntity, new: MemeEntity) =
      template.update(queries.getFromConfOrFail("updateMeme"),
          MapSqlParameterSource("oldChatId", old.chatId)
              .addValue("newChatId", new.chatId)
              .addValue("oldMessageId", old.messageId)
              .addValue("newMessageId", new.messageId)
              .addValue("oldSenderId", old.senderId)
              .addValue("newSenderId", new.senderId)
              .addValue("oldFileId", old.fileId)
              .addValue("newFileId", new.fileId)
              .addValue("oldChannelId", old.channelId)
              .addValue("newChannelId", new.channelId)
              .addValue("oldChannelMessageId", old.channelMessageId)
              .addValue("newChannelMessageId", new.channelMessageId)
      )

  private fun <T> checkZero(param: T): T? = if (param == 0 || param == 0L) null else param
}
