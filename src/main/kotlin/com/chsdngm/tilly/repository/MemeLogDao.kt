package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.dto.MemeLog
import com.chsdngm.tilly.model.dto.MemesLogs
import com.chsdngm.tilly.model.dto.toInsertStatement
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Repository

@Repository
class MemeLogDao {
    fun insert(memeLog: MemeLog) = transaction {
        MemesLogs.insert { memeLog.toInsertStatement(it) }.resultedValues?.first()
            ?: throw NoSuchElementException("Error saving meme log")
    }
}