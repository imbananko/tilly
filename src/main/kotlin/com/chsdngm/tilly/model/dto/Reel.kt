package com.chsdngm.tilly.model.dto

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement

data class Reel(
    val id: Int = 0,
    val url: String,
    val processed: Boolean = false
)

fun Reel.toInsertStatement(statement: InsertStatement<Number>) = statement.also {
    it[Reels.url] = this.url
    it[Reels.processed] = this.processed

}

fun Reel.toUpdateStatement(statement: UpdateStatement): UpdateStatement = statement.also {
    it[Reels.url] = this.url
    it[Reels.processed] = this.processed
}

fun ResultRow.toReel(): Reel? {
    if (this.getOrNull(Reels.id) == null) {
        return null
    }

    return Reel(
        id = this[Reels.id].value,
        url = this[Reels.url],
        processed = this[Reels.processed]
    )
}
