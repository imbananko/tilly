package com.imbananko.tilly.repository;

import com.imbananko.tilly.model.MemeEntity;
import com.imbananko.tilly.utility.SqlQueries;
import io.vavr.Tuple2;
import io.vavr.collection.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.WeakHashMap;

@Repository
public class MemeRepository {
  private final WeakHashMap<Tuple2<Long, Integer>, Integer> memeSenderCache;

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

    memeSenderCache.put(new Tuple2<>(memeEntity.getChatId(), memeEntity.getMessageId()), memeEntity.getSenderId());
  }

  public Integer getMemeSender(Long chatId, Integer messageId) {
    return memeSenderCache.computeIfAbsent(new Tuple2<>(chatId, messageId), param ->
      template.queryForObject(queries.getOrElse("findMemeSender", null),
        new MapSqlParameterSource("chatId", param._1).addValue("messageId", param._2), Integer.class)
    );
  }
}
