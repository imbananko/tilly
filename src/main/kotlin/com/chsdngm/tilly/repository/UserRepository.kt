package com.chsdngm.tilly.repository

import com.chsdngm.tilly.utility.SqlQueries
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.telegram.telegrambots.meta.api.objects.User

@Repository
class UserRepository(private val template: NamedParameterJdbcTemplate, private val queries: SqlQueries) {
  fun saveIfNotExists(user: User) = template.update(queries.getFromConfOrFail("insertUserIfNotExists"),
      MapSqlParameterSource("userId", user.id)
          .addValue("username", user.userName)
          .addValue("firstName", user.firstName)
          .addValue("lastName", user.lastName))
}
