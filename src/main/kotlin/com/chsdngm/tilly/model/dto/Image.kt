package com.chsdngm.tilly.model.dto

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement

data class Image(
    val fileId: String,
    val file: ByteArray,
    val hash: ByteArray,
    val rawText: String?,
    val rawLabels: String?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Image

        if (fileId != other.fileId) return false

        return true
    }

    override fun hashCode(): Int {
        return fileId.hashCode()
    }
}

fun Image.toInsertStatement(statement: InsertStatement<Number>): InsertStatement<Number> = statement.also {
    it[Images.fileId] = this.fileId
    it[Images.file] = this.file
    it[Images.hash] = this.hash
    it[Images.rawText] = this.rawText
    it[Images.rawLabels] = this.rawLabels
}

fun ResultRow.toImage(): Image? {
    if (this.getOrNull(Images.fileId) == null) {
        return null
    }

    return Image(
        fileId = this[Images.fileId],
        file = this[Images.file],
        hash = this[Images.hash],
        rawText = this[Images.rawText],
        rawLabels = this[Images.rawLabels]
    )
}
