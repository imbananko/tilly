package com.imbananko.tilly.repository;

import com.imbananko.tilly.model.MemeEntity;
import com.imbananko.tilly.utility.SqlQueries;
import io.vavr.collection.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MemeRepository {
  private final NamedParameterJdbcTemplate template;
  private final Map<String, String> queries;

  public MemeRepository(NamedParameterJdbcTemplate template, SqlQueries queries) {
    this.template = template;
    this.queries = queries.getQueries();
  }

  public void save(MemeEntity memeEntity) {
    template.update(queries.getOrElse("insertMeme", null),
      new MapSqlParameterSource("fileId", memeEntity.getFileId())
        .addValue("username", memeEntity.getAuthorUsername())
        .addValue("chatId", memeEntity.getTargetChatId()));
  }
}
