package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.Vote
import com.chsdngm.tilly.model.VoteKey
import org.springframework.data.repository.CrudRepository

interface VoteRepository : CrudRepository<Vote, VoteKey> {
  fun findVotesByKeyChatMessageId(chatMessageId: Int): List<Vote>
}