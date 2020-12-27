package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.Meme
import com.chsdngm.tilly.model.MemeStatus
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface MemeRepository : CrudRepository<Meme, Int> {
  fun findMemeByModerationChatIdAndModerationChatMessageId(chatId: Long, messageId: Int): Meme?

  fun findMemeByChannelMessageId(messageId: Int): Meme?

  fun findByFileId(fileId: String): Meme?

  @Query("""
        select distinct meme 
        from Meme meme 
        left join fetch meme.votes
        where meme.senderId = ?1
        """)
  fun findBySenderId(senderId: Int): List<Meme>

  fun findFirstByStatusOrderByCreated(memeStatus: MemeStatus = MemeStatus.SCHEDULED): Meme?

  @Query(value = "insert into meme_of_week (meme_id) values (:memeId)", nativeQuery = true)
  @Modifying
  @Transactional
  fun saveMemeOfWeek(@Param("memeId") memeId: Int): Unit

  @Query(value = """
    select m.*
    from meme m
             join vote v on m.id = v.meme_id
    where m.channel_message_id is not null
      and m.created > current_timestamp - interval '7 days'
    group by m.id
    order by count(value) filter (where value = 'UP') - count(value) filter (where value = 'DOWN') desc
    limit 1
  """, nativeQuery = true)
  fun findMemeOfTheWeek(): Meme?
}


