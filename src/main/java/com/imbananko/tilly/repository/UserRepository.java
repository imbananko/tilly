package com.imbananko.tilly.repository;

import com.imbananko.tilly.utility.SqlQueries;
import io.vavr.collection.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.telegram.telegrambots.meta.api.objects.User;

@Repository
public class UserRepository {
  private final NamedParameterJdbcTemplate template;
  private final Map<String, String> queries;

  public UserRepository(NamedParameterJdbcTemplate template, SqlQueries queries) {
    this.template = template;
    this.queries = queries.getQueries();
  }

  public void saveIfNotExists(User user) {
    template.update(queries.getOrElse("insertUserIfNotExists", null),
      new MapSqlParameterSource("userId", user.getId())
        .addValue("username", user.getUserName())
        .addValue("firstName", user.getFirstName())
        .addValue("lastName", user.getLastName()));
  }
}
