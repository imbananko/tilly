package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.TelegramUser
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : CrudRepository<TelegramUser, Long> {
  @Query(nativeQuery = true, value = """
    select u.*
    from meme m
             inner join (select meme_id,
                                count(*) filter (where value = 'UP')   up,
                                count(*) filter (where value = 'DOWN') down,
                                count(1)                               all_votes
                         from vote
                         where created >= now() - interval '7 days'
                         group by meme_id) v
                        on m.id = v.meme_id
             inner join telegram_user u on m.sender_id = u.id
    where m.channel_message_id is not null
      and created >= now() - interval '7 days'
      and u.id != :idToExclude
    group by u.id
    order by sum(up) - sum(down) - 2 * count(1) desc
    limit 5
    """)
  fun findTopSenders(@Param("idToExclude") idToExclude: Int): List<TelegramUser>
}