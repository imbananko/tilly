package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.TelegramUser
import org.springframework.data.jpa.repository.Query
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
interface UserRepository : CrudRepository<TelegramUser, Long>, UserRedisRepository {
  @Query(nativeQuery = true, value = """
    select u.*
    from meme m
             inner join (select chat_message_id,
                                count(*) filter (where value = 'UP')   up,
                                count(*) filter (where value = 'DOWN') down,
                                count(1)                               all_votes
                         from vote
                         where created_at >= now() - interval '7 days'
                         group by chat_message_id) v
                        on (m.chat_message_id = v.chat_message_id)
             inner join telegram_user u on m.sender_id = u.id
    where created_at >= now() - interval '7 days'
    group by u.id
    order by sum(up) - sum(down) - 2 * count(1) desc
    limit :limit
    """)
  fun findTopSenders(@Param("limit") count: Int): List<TelegramUser>
}

interface UserRedisRepository {
  val rankedModerationKey: String

  fun tryPickUserForModeration(userId: Int): Boolean
  fun isRankedModerationAvailable(): Boolean
}

internal class UserRedisRepositoryImpl(private val template: StringRedisTemplate) : UserRedisRepository {
  override val rankedModerationKey = "ranked-moderator-id"

  private val ops = template.opsForValue()

  override fun tryPickUserForModeration(userId: Int) =
      if (ops.get("$rankedModerationKey-$userId") == null) {
        ops.set("$rankedModerationKey-$userId", "", Duration.ofDays(1))
        true
      } else
        false

  override fun isRankedModerationAvailable() = ops.operations.keys("$rankedModerationKey*")?.size ?: 0 < 5
}