@file:DependsOn("org.postgresql:postgresql:42.2.6")
@file:DependsOn("org.telegram:telegrambots-spring-boot-starter:4.8.1")

import com.chsdngm.tilly.similarity.ImageMatcher
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.script.experimental.dependencies.DependsOn

val dbUrl = ""
val user = ""
val password = ""

object Image : Table() {
  val fileId = varchar("file_id", 200)
  val file = binary("file", length = 1000)
  val hash = binary("hash", length = 1000)
}

Database.connect(dbUrl, driver = "org.postgresql.Driver", user = user, password = password)

transaction {
  var i = 0;
  Image.selectAll().map { it[Image.fileId] to it[Image.file] }.also { println("fetched urls from db. size=${it.size}") }.forEach { pair ->
    Image.update({ Image.fileId eq pair.first }) {
      it[hash] = ImageMatcher.calculateHash(pair.second)
      println(++i)
    }
  }
}
