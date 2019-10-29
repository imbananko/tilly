package com.imbananko.tilly.repository;

import com.imbananko.tilly.model.MemeEntity;
import com.imbananko.tilly.utility.SqlQueries;
import io.vavr.collection.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;

@Repository
public class MemeRepository {
  private final WeakHashMap<Integer, Integer> memeSenderCache;

  private final NamedParameterJdbcTemplate template;
  private final Map<String, String> queries;

  public MemeRepository(NamedParameterJdbcTemplate template, SqlQueries queries) {
    this.template = template;
    this.queries = queries.getQueries();

    this.memeSenderCache = new WeakHashMap<>();
  }

  public void save(MemeEntity memeEntity) {
    template.update(queries.getOrElse("insertMeme", null),
      new MapSqlParameterSource("chatId", memeEntity.getChatId())
        .addValue("messageId", memeEntity.getMessageId())
        .addValue("senderId", memeEntity.getSenderId())
        .addValue("fileId", memeEntity.getFileId()));

    memeSenderCache.put(Objects.hash(memeEntity.getChatId(), memeEntity.getMessageId()), memeEntity.getSenderId());
  }

  public Integer getMemeSender(long chatId, int messageId) {
    return memeSenderCache.computeIfAbsent(Objects.hash(chatId, messageId), ignored ->
      template.queryForObject(queries.getOrElse("findMemeSender", null),
        new MapSqlParameterSource("chatId", chatId).addValue("messageId", messageId), Integer.class)
    );
  }

  public List<MemeEntity> load(long chatId) {
    return template.query(
        queries.get("loadMemes").get(),
        new MapSqlParameterSource("chat_id", chatId),
        (rs, rowNum) ->
            MemeEntity.builder()
                .senderId((int) rs.getLong("sender_id"))
                .fileId(rs.getString("file_id"))
                .chatId(rs.getLong("chat_id"))
                .build());
  }
}
