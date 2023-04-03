package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.dto.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Repository
import java.sql.SQLException

@Repository
class VoteDao(val database: Database) {
    fun insert(vote: Vote) = transaction {
        Votes.insert { vote.toInsertStatement(it) }.resultedValues?.first()?.toVote()
            ?: throw SQLException("Error saving vote")
    }

    fun delete(vote: Vote) = transaction {
        Votes.deleteWhere { (Votes.memeId eq vote.memeId) and (Votes.voterId eq vote.voterId) }
    }

    fun update(vote: Vote) = transaction {
        Votes.update({ (Votes.memeId eq vote.memeId) and (Votes.voterId eq vote.voterId) }) { vote.toUpdateStatement(it) }
    }

    fun findAllByVoterId(voterId: Long): List<Vote> = transaction {
        Votes.select { Votes.voterId eq voterId }.mapNotNull { it.toVote() }
    }
}
