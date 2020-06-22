package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.Vote
import org.springframework.data.repository.CrudRepository

interface VoteRepository : CrudRepository<Vote, Vote.VoteKey> {
  fun findVotesByChatMessageId(chatMessageId: Int): List<Vote>
}