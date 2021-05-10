@file:DependsOn("org.postgresql:postgresql:42.2.6")
@file:DependsOn("org.ktorm:ktorm-core:3.3.0")

import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.vision.v1.*
import com.google.protobuf.ByteString
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.schema.Table
import org.ktorm.schema.blob
import org.ktorm.schema.varchar
import org.ktorm.support.postgresql.textArray
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.*
import kotlin.script.experimental.dependencies.DependsOn


val dbUrl = ""
val user = ""
val password = ""
val googleKey = ""

val database = Database.connect(dbUrl, user = user, password = password)

object Images : Table<Nothing>("image") {
  val fileId = varchar("file_id").primaryKey()
  val file = blob("file")
  val words = textArray("words")
}

println("осталось: ${database.from(Images).select().where { Images.words.isNull() }.totalRecords}")

database.from(Images)
  .select()
  .where { Images.words.isNull() }
  .limit(1000)
  .forEach { row ->
    val bytes = row[Images.file]!!

    val text = detectText(bytes).toTypedArray()

    database.update(Images) {
      set(it.words, text)
      where {
        it.fileId eq row[Images.fileId]!!
      }
    }
  }

@Throws(Exception::class, IOException::class)
fun detectText(imageByteArray: ByteArray): List<String> {
  val requests: MutableList<AnnotateImageRequest> = ArrayList<AnnotateImageRequest>()
  val imgBytes: ByteString = ByteString.copyFrom(imageByteArray)

  val img: Image = Image.newBuilder().setContent(imgBytes).build()
  val feat = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build()
  val request: AnnotateImageRequest = AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build()

  val provider = FixedCredentialsProvider.create(
    GoogleCredentials.fromStream(
      ByteArrayInputStream(Base64.getDecoder().decode(googleKey))
    ).createScoped(listOf("https://www.googleapis.com/auth/cloud-vision")))

  val imageAnnotatorSettings = ImageAnnotatorSettings.newBuilder()
    .setCredentialsProvider(FixedCredentialsProvider.create(provider.credentials))
    .build()

  requests.add(request)
  ImageAnnotatorClient.create(imageAnnotatorSettings).use { client ->
    val response: BatchAnnotateImagesResponse = client.batchAnnotateImages(requests)
    val responses: List<AnnotateImageResponse> = response.responsesList
    for (res in responses) {
      if (res.hasError()) {
        return listOf()
      }

      if (res.textAnnotationsList.isEmpty()) return listOf()
      println(res.textAnnotationsList[0].description)
      return res.textAnnotationsList.drop(1).map { word -> word.description }
    }
  }

  return listOf()
}