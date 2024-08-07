package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.dto.Image
import com.chsdngm.tilly.model.dto.Images
import com.chsdngm.tilly.model.dto.toImage
import com.chsdngm.tilly.model.dto.toInsertStatement
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Repository
import java.math.BigInteger
import java.sql.SQLException

@Repository
class ImageDao(val database: Database) {
    suspend fun insert(image: Image) = newSuspendedTransaction {
        Images.insert { image.toInsertStatement(it) }.resultedValues?.first()?.toImage()
            ?: throw SQLException("Error saving image")
    }

    fun findAllHashes(): Map<String, BigInteger> = transaction {
        Images.slice(Images.fileId, Images.hash)
            .selectAll()
            .associate { it[Images.fileId] to BigInteger(it[Images.hash]) }
    }
}