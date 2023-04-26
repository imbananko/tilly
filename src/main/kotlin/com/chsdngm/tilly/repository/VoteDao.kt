package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.dto.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.springframework.stereotype.Repository

@Repository
class VoteDao(val database: Database) {
    suspend fun insert(vote: Vote) = newSuspendedTransaction {
        Votes.insert { vote.toInsertStatement(it) }.resultedValues?.first()?.toVote()
            ?: throw NoSuchElementException("Error saving vote")
    }

    suspend fun delete(vote: Vote) = newSuspendedTransaction {
        Votes.deleteWhere { (Votes.memeId eq vote.memeId) and (Votes.voterId eq vote.voterId) }
    }

    suspend fun update(vote: Vote) = newSuspendedTransaction {
        Votes.update({ (Votes.memeId eq vote.memeId) and (Votes.voterId eq vote.voterId) }) { vote.toUpdateStatement(it) }
    }

    suspend fun findAllByVoterId(voterId: Long): List<Vote> = newSuspendedTransaction {
        Votes.select { Votes.voterId eq voterId }.mapNotNull { it.toVote() }
    }
}
