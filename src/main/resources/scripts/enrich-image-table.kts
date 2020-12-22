@file:DependsOn("org.postgresql:postgresql:42.2.6")
@file:DependsOn("org.telegram:telegrambots-spring-boot-starter:4.8.1")

import org.apache.commons.io.IOUtils
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.meta.ApiContext
import org.telegram.telegrambots.meta.api.methods.GetFile
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.script.experimental.dependencies.DependsOn

val token = "dumb"
val dbUrl = "dumb"
val user = "dumb"
val password = "dumb"

object Image : Table() {
  val fileId = varchar("file_id", 200)
  val file = binary("file", length = 1000)
}

object Meme : Table() {
  val fileId = varchar("file_id", 100)
}

object Bot : DefaultAbsSender(ApiContext.getInstance(DefaultBotOptions::class.java)) {
  override fun getBotToken(): String = token

  fun downloadFromFileId(fileId: String): File =
      File.createTempFile("photo-", "-" + Thread.currentThread().id + "-" + System.currentTimeMillis()).apply { this.deleteOnExit() }.also {
        FileOutputStream(it).use { out ->
          URL(execute(GetFile().setFileId(fileId)).getFileUrl(botToken)).openStream().use { stream -> IOUtils.copy(stream, out) }
        }
      }
}

Database.connect(dbUrl, driver = "org.postgresql.Driver", user = user, password = password)

var urls: List<String> = emptyList()

transaction {
  urls = Meme.selectAll().map { it[Meme.fileId] }.also { println("fetched urls from db. size=${it.size}") }
}

var map = ConcurrentHashMap<String, File>()

urls.parallelStream().forEach { url ->
  runCatching {
    Bot.downloadFromFileId(url)
  }.onSuccess {
    map[url] = it
    println("downloaded $url")
  }.onFailure {
    println("failed to download $url")
  }
}

println("pairs size=${map.size}")

transaction {
  Image.batchInsert(map.asIterable()) { entry ->
    this[Image.fileId] = entry.key
    this[Image.file] = entry.value.readBytes()
  }
}
