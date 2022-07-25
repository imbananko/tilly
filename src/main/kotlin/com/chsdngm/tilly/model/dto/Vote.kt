package com.chsdngm.tilly.model.dto

import com.chsdngm.tilly.model.VoteValue
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import java.time.Instant

data class Vote(
    val memeId: Int,
    val voterId: Int,
    var sourceChatId: Long,
    var value: VoteValue,
    val created: Instant = Instant.now(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Vote

        if (memeId != other.memeId) return false
        if (voterId != other.voterId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = memeId
        result = 31 * result + voterId
        return result
    }
}

fun ResultRow.toVote(): Vote? {
    if (this.getOrNull(Votes.memeId) == null || this.getOrNull(Votes.voterId) == null) {
        return null
    }

    return Vote(
        memeId = this[Votes.memeId],
        voterId = this[Votes.voterId],
        value = this[Votes.value],
        sourceChatId = this[Votes.sourceChatId],
        created = this[Votes.created]
    )
}

fun Vote.toInsertStatement(statement: InsertStatement<Number>): InsertStatement<Number> = statement.also {
    it[Votes.voterId] = this.voterId
    it[Votes.memeId] = this.memeId
    it[Votes.value] = this.value
    it[Votes.sourceChatId] = this.sourceChatId
    it[Votes.created] = this.created
}

fun Vote.toUpdateStatement(statement: UpdateStatement): UpdateStatement = statement.also {
    it[Votes.value] = this.value
    it[Votes.sourceChatId] = this.sourceChatId
    it[Votes.created] = this.created
}