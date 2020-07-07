package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.Meme
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface MemeRepository : CrudRepository<Meme, Long> {
  fun findMemeByChatMessageId(messageId: Int): Meme?

  fun findMemeByChannelMessageId(messageId: Int): Meme?

  fun findByFileId(fileId: String): Meme?

  @Query("""
        select distinct meme 
        from Meme meme 
        left join fetch meme.votes
        where meme.senderId = ?1
        """)
  fun findBySenderId(senderId: Int): List<Meme>

  @Query(value = "insert into meme_of_week (chat_message_id) values (:chatMessageId)", nativeQuery = true)
  fun saveMemeOfWeek(@Param("chatMessageId") chatMessageId: Int)

  @Query(value = """
    select m.*
    from meme m
             join vote v on m.chat_message_id = v.chat_message_id
    where m.created_at > current_timestamp - interval '7 days'
    group by m.chat_message_id
    order by count(value) filter (where value = 'UP') - count(value) filter (where value = 'DOWN') desc
    limit 1
  """, nativeQuery = true)
  fun findMemeOfTheWeek(): Meme?
}


