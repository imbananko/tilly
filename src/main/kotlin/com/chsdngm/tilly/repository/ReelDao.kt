package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.dto.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.springframework.stereotype.Repository
import java.sql.SQLException

@Repository
class ReelDao(val database: Database) {
    fun insert(reel: Reel) = transaction {
        Reels.insert { reel.toInsertStatement(it) }.resultedValues?.first()?.toReel()
            ?: throw SQLException("Error saving reel")
    }

    fun update(reel: Reel) = transaction {
        Reels.update({ Reels.id eq reel.id }) { reel.toUpdateStatement(it) }
    }
}