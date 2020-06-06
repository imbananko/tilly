package com.chsdngm.tilly.repository

import com.chsdngm.tilly.utility.SqlQueries
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.telegram.telegrambots.meta.api.objects.User
import java.util.*


@Repository
class UserRepository(private val template: NamedParameterJdbcTemplate,
                     redisTemplate: RedisTemplate<String, Int>,
                     private val queries: SqlQueries) {

  val set: ZSetOperations<String, Int> = redisTemplate.opsForZSet()

  val oneDay: Long = 1000 * 60 * 60 * 24
  val rankedModerationKey = "restricted_for_moderation_users"

  fun saveIfNotExists(user: User) = template.update(queries.getFromConfOrFail("insertUserIfNotExists"),
      MapSqlParameterSource("userId", user.id)
          .addValue("username", user.userName)
          .addValue("firstName", user.firstName)
          .addValue("lastName", user.lastName))


  fun restrictModerationForUser(userId: Int): Boolean {
    val now = Date().time

    return if (now - (set.score(rankedModerationKey, userId)?.toLong() ?: 0) > oneDay) {
      set.add(rankedModerationKey, userId, now.toDouble())
      true
    } else
      false
  }

  fun isRankedModerationAvailable(): Boolean {
    val now = Date().time
    return set.count(rankedModerationKey, now.minus(oneDay).toDouble(), now.toDouble()) ?: 0 < 5
  }
}
