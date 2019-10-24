package com.imbananko.tilly.repository;

import com.imbananko.tilly.model.MemeEntity;
import com.imbananko.tilly.utility.SqlQueries;
import io.vavr.collection.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.WeakHashMap;

@Repository
public class MemeRepository {
  private final WeakHashMap<String, Integer> memeSenderCache;

  private final NamedParameterJdbcTemplate template;
  private final Map<String, String> queries;

  public MemeRepository(NamedParameterJdbcTemplate template, SqlQueries queries) {
    this.template = template;
    this.queries = queries.getQueries();

    this.memeSenderCache = new WeakHashMap<>();
  }

  public void save(MemeEntity memeEntity) {
    template.update(queries.getOrElse("insertMeme", null),
      new MapSqlParameterSource("memeId", memeEntity.getMemeId())
        .addValue("senderId", memeEntity.getSenderId())
        .addValue("fileId", memeEntity.getFileId())
        .addValue("username", memeEntity.getAuthorUsername())
        .addValue("chatId", memeEntity.getTargetChatId()));

    memeSenderCache.put(memeEntity.getMemeId(), memeEntity.getSenderId());
  }

  public Integer getMemeSender(String memeId) {
    return memeSenderCache.computeIfAbsent(memeId, param ->
      template.queryForObject(queries.getOrElse("findMemeSender", null), new MapSqlParameterSource("memeId", param), Integer.class)
    );
  }
}
