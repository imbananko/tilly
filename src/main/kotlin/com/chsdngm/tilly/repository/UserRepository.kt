package com.chsdngm.tilly.repository

import com.chsdngm.tilly.utility.SqlQueries
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.telegram.telegrambots.meta.api.objects.User
import java.time.Duration


@Repository
class UserRepository(private val template: NamedParameterJdbcTemplate,
                     redisTemplate: StringRedisTemplate,
                     private val queries: SqlQueries) {

  val ops: ValueOperations<String, String> = redisTemplate.opsForValue()
  val rankedModerationKey = "ranked-moderator-id"

  fun saveIfNotExists(user: User) = template.update(queries.getFromConfOrFail("insertUserIfNotExists"),
      MapSqlParameterSource("userId", user.id)
          .addValue("username", user.userName)
          .addValue("firstName", user.firstName)
          .addValue("lastName", user.lastName))


  fun tryPickUserForModeration(userId: Int) =
      if (ops.get("$rankedModerationKey-$userId") == null) {
        ops.set("$rankedModerationKey-$userId", "", Duration.ofDays(1))
        true
      } else
        false

  fun isRankedModerationAvailable() = ops.operations.keys("$rankedModerationKey*")?.size ?: 0 < 5
}
