package com.imbananko.tilly.utility

import com.imbananko.tilly.MemeManager
import com.imbananko.tilly.model.VoteValue
import org.apache.commons.io.IOUtils
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.objects.Update
import java.io.File
import java.io.FileOutputStream
import java.net.URL

fun SqlQueries?.getFromConfOrFail(key: String): String =
        if (this == null) error("SqlQueries object should not be null")
        else this.queries[key] ?: error("Configuration should contain `$key`")

fun Update.isP2PChat() = this.hasMessage() && this.message.chat.isUserChat

fun Update.hasPhoto() = this.hasMessage() && this.message.hasPhoto()

fun Update.hasVote() =
        this.hasCallbackQuery() && runCatching {
            setOf(*VoteValue.values()).contains(extractVoteValue())
        }.getOrDefault(false)

fun Update.extractVoteValue() =
        VoteValue.valueOf(this.callbackQuery.data.split(" ".toRegex()).dropLastWhile { it.isEmpty() }[0])

fun MemeManager.downloadFromFileId(fileId: String): File {
    val telegramFile = execute(GetFile().apply { this.fileId = fileId })
    val tempFile = File.createTempFile("telegram-photo-", "").apply { this.deleteOnExit() }

    FileOutputStream(tempFile)
        .use { out ->
            URL(telegramFile.getFileUrl(botToken)).openStream().use { IOUtils.copy(it, out) }
        }

    return tempFile
}