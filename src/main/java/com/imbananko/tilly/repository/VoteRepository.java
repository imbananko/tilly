package com.imbananko.tilly.repository;

import com.imbananko.tilly.model.VoteEntity;
import com.imbananko.tilly.model.VoteEntity.Value;
import com.imbananko.tilly.utility.SqlQueries;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VoteRepository {

  private final NamedParameterJdbcTemplate template;
  private final Map<String, String> queries;

  public VoteRepository(NamedParameterJdbcTemplate template, SqlQueries queries) {
    this.template = template;
    this.queries = queries.getQueries();
  }

  @SuppressWarnings("ConstantConditions")
  public boolean exists(VoteEntity vote) {
    return template.queryForObject(queries.getOrElse("voteExists", null), getParams(vote), Boolean.class);
  }

  public void insertOrUpdate(VoteEntity vote) {
    template.update(queries.getOrElse("insertOrUpdateVote", null), getParams(vote));
  }

  public void delete(VoteEntity vote) {
    template.update(queries.getOrElse("deleteVote", null), getParams(vote));
  }

  public HashMap<Value, Long> getStats(String memeId) {
    return HashMap.ofEntries(template.query(queries.getOrElse("findVoteStats", null),
      new MapSqlParameterSource("memeId", memeId),
      (rs, rowNum) ->
        new Tuple2<>(Value.valueOf(rs.getString("value")), rs.getLong("count"))));
  }

  private MapSqlParameterSource getParams(VoteEntity vote) {
    return new MapSqlParameterSource("memeId", vote.getMemeId())
      .addValue("voterId", vote.getVoterId())
      .addValue("chatId", vote.getChatId())
      .addValue("fileId", vote.getFileId())
      .addValue("value", vote.getValue().name())
      .addValue("username", vote.getUsername());
  }
}