package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.MemeEntity
import com.chsdngm.tilly.utility.SqlQueries
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
        MapSqlParameterSource("chatMessageId", meme.chatMessageId)
            .addValue("senderId", meme.senderId)
            .addValue("fileId", meme.fileId)
            .addValue("privateMessageId", meme.privateMessageId)
            .addValue("caption", meme.caption))

    memeSenderCache[meme.chatMessageId] = meme.senderId
    return meme
  }

  fun findByChatMessageId(chatMessageId: Int): MemeEntity? =
      template.queryForObject(queries.getFromConfOrFail("findMemeByMessageId"),
          MapSqlParameterSource("chatMessageId", chatMessageId))
      { rs, _ ->
        MemeEntity(rs.getInt("chat_message_id"),
            rs.getInt("sender_id"),
            rs.getString("file_id"),
            rs.getString("caption"),
            checkZero(rs.getInt("private_message_id")),
            checkZero(rs.getInt("channel_message_id")))
      }

  fun findByChannelMessageId(channelMessageId: Int): MemeEntity? =
      template.queryForObject(queries.getFromConfOrFail("findMemeByChannelMessageId"),
          MapSqlParameterSource("channelMessageId", channelMessageId))
      { rs, _ ->
        MemeEntity(rs.getInt("chat_message_id"),
            rs.getInt("sender_id"),
            rs.getString("file_id"),
            rs.getString("caption"),
            checkZero(rs.getInt("private_message_id")),
            checkZero(rs.getInt("channel_message_id")))
      }

  fun findByFileId(filedId: String): MemeEntity? =
      template.queryForObject(queries.getFromConfOrFail("findMemeByFileId"),
          MapSqlParameterSource("fileId", filedId))
      { rs, _ ->
        MemeEntity(rs.getInt("chat_message_id"),
            rs.getInt("sender_id"),
            rs.getString("file_id"),
            rs.getString("caption"),
            checkZero(rs.getInt("private_message_id")),
            checkZero(rs.getInt("channel_message_id")))
      }

  fun findAll(): List<MemeEntity> =
      template.query(queries.getFromConfOrFail("selectAllMemes"))
      { rs: ResultSet, _: Int ->
        MemeEntity(rs.getInt("chat_message_id"),
            rs.getInt("sender_id"),
            rs.getString("file_id"),
            rs.getString("caption"),
            checkZero(rs.getInt("private_message_id")),
            checkZero(rs.getInt("channel_message_id")))
      }

  fun findMemeOfTheWeek(): MemeEntity? =
      template.queryForObject(queries.getFromConfOrFail("getMemeOfTheWeek"), MapSqlParameterSource())
      { rs: ResultSet, _: Int ->
        MemeEntity(rs.getInt("chat_message_id"),
            rs.getInt("sender_id"),
            rs.getString("file_id"),
            rs.getString("caption"),
            checkZero(rs.getInt("private_message_id")),
            checkZero(rs.getInt("channel_message_id")))
      }

  fun findMemesOfTheYear(): List<MemeEntity> =
      template.query(queries.getFromConfOrFail("getMemesOfTheYear"))
      { rs: ResultSet, _: Int ->
        MemeEntity(rs.getInt("chat_message_id"),
            rs.getInt("sender_id"),
            rs.getString("file_id"),
            rs.getString("caption"),
            checkZero(rs.getInt("private_message_id")),
            checkZero(rs.getInt("channel_message_id")))
      }.toList()

  fun markAsMemeOfTheWeek(meme: MemeEntity) =
      template.update(queries.getFromConfOrFail("insertMemeOfWeek"),
          MapSqlParameterSource("chatMessageId", meme.chatMessageId))

  fun update(old: MemeEntity, new: MemeEntity) =
      template.update(queries.getFromConfOrFail("updateMeme"),
          MapSqlParameterSource("oldMessageId", old.chatMessageId)
              .addValue("newMessageId", new.chatMessageId)
              .addValue("oldSenderId", old.senderId)
              .addValue("newSenderId", new.senderId)
              .addValue("oldFileId", old.fileId)
              .addValue("newFileId", new.fileId)
              .addValue("oldChannelMessageId", old.channelMessageId)
              .addValue("newChannelMessageId", new.channelMessageId)
      )

  //Temporary placed here due to dependency injections
  fun getRating(): Map<Int, Int> = template.query(queries.getFromConfOrFail("selectRating")) { rs, _ ->
    rs.getInt("sender_id") to rs.getInt("rating")
  }.toMap()

  private fun <T> checkZero(param: T): T? = if (param == 0 || param == 0L) null else param
}
