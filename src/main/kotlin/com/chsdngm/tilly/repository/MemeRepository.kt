package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.Meme
import com.chsdngm.tilly.model.MemeStatus
import com.chsdngm.tilly.utility.TillyConfig
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface MemeRepository : CrudRepository<Meme, Int> {
  @Query("""
        select distinct meme 
        from Meme meme 
        left join fetch meme.votes
        where meme.moderationChatId = ?1 and meme.moderationChatMessageId = ?2
        """)
  fun findMemeByModerationChatIdAndModerationChatMessageId(chatId: Long, messageId: Int): Meme?

  @Query("""
        select distinct meme 
        from Meme meme 
        left join fetch meme.votes
        where meme.channelMessageId = ?1
        """)
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
  fun saveMemeOfWeek(@Param("memeId") memeId: Int)

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

  @Query(value = """
    select memeWithVotes.*
    from (select meme.*,
                 count(vote) filter ( where vote.value = 'UP' )   as ups,
                 count(vote) filter ( where vote.value = 'DOWN' ) as downs
          from meme
                   left join vote on meme.id = vote.meme_id
          where meme.created between now() - interval '7 days' and now() - interval '1 days'
            and meme.status = 'MODERATION'
            and moderation_chat_id = (:moderationChatId)
          group by meme.id) as memeWithVotes
    where ((memeWithVotes.ups - memeWithVotes.downs) = :moderationThreshold - 1
       or (memeWithVotes.ups + memeWithVotes.downs) < :moderationThreshold) and (memeWithVotes.ups - memeWithVotes.downs) > -3
    order by memeWithVotes.created
    limit 5
  """, nativeQuery = true)
  fun findForgottenMemes(
      @Param("moderationChatId") moderationChatId: Long = TillyConfig.CHAT_ID.toLong(),
      @Param("moderationThreshold") moderationThreshold: Long = TillyConfig.MODERATION_THRESHOLD,
  ): List<Meme>
}


