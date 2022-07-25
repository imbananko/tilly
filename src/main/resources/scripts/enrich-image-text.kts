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
import org.ktorm.schema.text
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
  val labels = textArray("labels")
  val rawText = text("raw_text")
}

println("осталось: ${database.from(Images).select().where { Images.words.isNull() }.totalRecords}")

var x = 0;
for (i in 0..16) {
  database.from(Images)
    .select()
    .where { Images.rawText.isNull() }
    .limit(1000)
    .offset(1000 * i)
    .forEach { row ->
      println("i=$i x=${x++}")
      val bytes = row[Images.file]!!

      val textToLabels = detectText(bytes)

      database.update(Images) {
        set(it.rawText, textToLabels.first)
        set(it.labels, textToLabels.second?.toTypedArray())
        where {
          it.fileId eq row[Images.fileId]!!
        }
      }
    }
}

@Throws(Exception::class, IOException::class)
fun detectText(imageByteArray: ByteArray): Pair<String?, List<String>?> {
  val requests: MutableList<AnnotateImageRequest> = ArrayList<AnnotateImageRequest>()
  val imgBytes: ByteString = ByteString.copyFrom(imageByteArray)

  val img: Image = Image.newBuilder().setContent(imgBytes).build()
  val textFeature = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build()
  val labelsFeature = Feature.newBuilder().setType(Feature.Type.LABEL_DETECTION).build()
  val request: AnnotateImageRequest = AnnotateImageRequest.newBuilder()
    .addFeatures(textFeature)
    .addFeatures(labelsFeature)
    .setImage(img).build()

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
        return null to null
      }

      val text = res.textAnnotationsList.firstOrNull()?.description
      val labels = if (res.labelAnnotationsList.isNotEmpty()) res.labelAnnotationsList.map { it.description } else null
      return text to labels
    }
  }

  return null to null
}