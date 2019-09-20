package com.imbananko.tilly.repository;

import com.imbananko.tilly.model.MemeEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@SuppressWarnings("SqlDialectInspection")
@Repository
public class MemeRepository {
  private final NamedParameterJdbcTemplate template;

  public MemeRepository(NamedParameterJdbcTemplate template) {
    this.template = template;
  }

  public void save(MemeEntity memeEntity) {
    var query = "insert into meme (file_id, author_username, target_chat_id) values (:fileId, :username, :chatId)";
    template.update(
        query,
        new MapSqlParameterSource("fileId", memeEntity.getFileId())
            .addValue("username", memeEntity.getAuthorUsername())
            .addValue("chatId", memeEntity.getTargetChatId()));
  }

  public Optional<MemeEntity> findByFileId(String fileId) {
    var query = "select file_id, author_username, target_chat_id from meme where file_id = :fileId";
    return Optional.ofNullable(
        template.queryForObject(
            query,
            new MapSqlParameterSource("fileId", fileId),
            (rs, rowNum) ->
                MemeEntity.builder()
                    .authorUsername(rs.getString("author_username"))
                    .fileId(rs.getString("file_id"))
                    .targetChatId(rs.getLong("target_chat_id"))
                    .build()));
  }
}
