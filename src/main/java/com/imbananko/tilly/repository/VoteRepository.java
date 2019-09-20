package com.imbananko.tilly.repository;

import com.imbananko.tilly.model.VoteEntity;
import com.imbananko.tilly.model.VoteEntity.Value;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@SuppressWarnings({"ConstantConditions", "SqlResolve"})
@Repository
public class VoteRepository {

  private final NamedParameterJdbcTemplate template;

  public VoteRepository(NamedParameterJdbcTemplate template) {
    this.template = template;
  }

  public boolean exists(VoteEntity vote) {
    String query = "select exists(select 1 from vote where file_id = :fileId and chat_id = :chatId and value = :value and username = :username)";
    return template.queryForObject(query,
        new MapSqlParameterSource("chatId", vote.getChatId())
            .addValue("fileId", vote.getFileId())
            .addValue("value", vote.getValue().name())
            .addValue("username", vote.getUsername()), Boolean.class);
  }

  public void insertOrUpdate(VoteEntity vote) {
    String query = "insert into vote(file_id, chat_id, username, value) "
        + "values (:fileId, :chatId, :username, :value) "
        + "on conflict (chat_id, file_id, username) do update set value = :value";
    template.update(query, new MapSqlParameterSource("chatId", vote.getChatId())
        .addValue("fileId", vote.getFileId())
        .addValue("value", vote.getValue().name())
        .addValue("username", vote.getUsername()));
  }

  public void delete(VoteEntity vote) {
    String query = "delete from vote where file_id = :fileId and chat_id = :chatId and username = :username and value = :value";
    template.update(query, new MapSqlParameterSource("chatId", vote.getChatId())
        .addValue("fileId", vote.getFileId())
        .addValue("value", vote.getValue().name())
        .addValue("username", vote.getUsername()));
  }

  public HashMap<Value, Long> getStats(String fileId, long chatId) {
    String query = "select value, count(value) from vote where file_id = :fileId and chat_id = :chatId group by value";
    return HashMap.ofEntries(template.query(query,
        new MapSqlParameterSource("chatId", chatId).addValue("fileId", fileId),
        (rs, rowNum) ->
            new Tuple2<>(Value.valueOf(rs.getString("value")), rs.getLong("count"))));
  }
}