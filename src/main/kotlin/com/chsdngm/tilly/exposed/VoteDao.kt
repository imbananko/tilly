package com.chsdngm.tilly.exposed

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Repository

@Repository
class VoteDao(val database: Database) {
    fun insert(vote: Vote): Vote = transaction {
        Votes.insert { vote.toInsertStatement(it) }.resultedValues?.first()?.toVote()
            ?: throw NoSuchElementException("Error saving vote")
    }

    fun delete(vote: Vote) = transaction {
        Votes.deleteWhere { (Votes.memeId eq vote.memeId) and (Votes.voterId eq vote.voterId) }
    }

    fun update(vote: Vote) = transaction {
        Votes.update { vote.toUpdateStatement(it) }
    }
}