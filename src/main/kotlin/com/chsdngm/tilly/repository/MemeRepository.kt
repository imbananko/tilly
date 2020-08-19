package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.Meme
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface MemeRepository : CrudRepository<Meme, Long> {
  fun findMemeByModerationChatIdAndChatMessageId(moderationChatId: Long, messageId: Int): Meme?

  fun findMemeByChannelMessageId(messageId: Int): Meme?

  fun findByFileId(fileId: String): Meme?

  @Query("""
        select distinct meme 
        from Meme meme 
        left join fetch meme.votes
        where meme.senderId = ?1
        """)
  fun findBySenderId(senderId: Int): List<Meme>

  // 2020-08-19 19:00:00 UTC is 2020-08-19 22:00:00 in Moscow (meme of the week)
  @Query("""
        select count(*) from meme where sender_id = :senderId and created_at >= '2020-08-19 19:00:00' 
        """, nativeQuery = true)
  fun memesAfterContestStarted(@Param("senderId") senderId: Int): Int

  @Query(value = "insert into meme_of_week (channel_message_id) values (:channelMessageId)", nativeQuery = true)
  @Modifying
  @Transactional
  fun saveMemeOfWeek(@Param("channelMessageId") channelMessageId: Int): Unit

  @Query(value = """
    select m.*
    from meme m
             join vote v on m.moderation_chat_id = v.moderation_chat_id and m.chat_message_id = v.chat_message_id
    where m.channel_message_id is not null and m.created_at > current_timestamp - interval '7 days'
    group by m.channel_message_id, m.chat_message_id, m.sender_id, m.file_id, m.created_at, m.private_message_id, m.caption, m.moderation_chat_id
    order by count(value) filter (where value = 'UP') - count(value) filter (where value = 'DOWN') desc
    limit 1
  """, nativeQuery = true)
  fun findMemeOfTheWeek(): Meme?
}


