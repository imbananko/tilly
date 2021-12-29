@file:DependsOn("org.postgresql:postgresql:42.2.6")
@file:DependsOn("org.ktorm:ktorm-core:3.3.0")

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.schema.Table
import org.ktorm.schema.blob
import org.ktorm.schema.varchar
import java.io.ByteArrayInputStream
import java.util.*
import kotlin.script.experimental.dependencies.DependsOn

val dbUrl = ""
val user = ""
val password = ""
val projectId = ""
val googleKey = ""

val storage: Storage = StorageOptions
    .newBuilder()
    .setCredentials(
        GoogleCredentials.fromStream(
            ByteArrayInputStream(Base64.getDecoder().decode(googleKey))
        )
    )
    .setProjectId(projectId).build().service

val database = Database.connect(dbUrl, user = user, password = password)

object Images : Table<Nothing>("image") {
    val fileId = varchar("file_id").primaryKey()
    val file = blob("file")
}

for (i in 0..14) {
    database.from(Images)
        .select()
        .limit(1000)
        .offset(1000 * i)
        .forEach { row ->
            println(row[Images.fileId])

            val blobId = BlobId.of("tilly", row[Images.fileId])
            val blobInfo = BlobInfo.newBuilder(blobId).build()
            storage.create(blobInfo, row[Images.file])
        }
}
