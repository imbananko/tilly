@file:DependsOn("org.postgresql:postgresql:42.2.6", "org.ktorm:ktorm-core:3.3.0")

import org.apache.http.HttpHost
import org.apache.http.client.config.RequestConfig
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.schema.Table
import org.ktorm.schema.blob
import org.ktorm.schema.varchar
import org.ktorm.support.postgresql.textArray
import org.springframework.context.annotation.DependsOn


val dbUrl = ""
val user = ""
val password = ""
val projectId = ""
val googleKey = ""
val elasticsearchClient = ""

println(RequestConfig.DEFAULT)
//
//val storage: Storage = StorageOptions
//    .newBuilder()
//    .setCredentials(
//        GoogleCredentials.fromStream(
//            ByteArrayInputStream(Base64.getDecoder().decode(googleKey))
//        )
//    )
//    .setProjectId(projectId).build().service

val database = Database.connect(dbUrl, user = user, password = password)

object Images : Table<Nothing>("image") {
    val fileId = varchar("file_id").primaryKey()
    val file = blob("file")
    val words = textArray("words")
}

RestHighLevelClient(
    RestClient
        .builder(
            HttpHost(elasticsearchClient, 9200, "http")
        )
).use {
    for (i in 14 downTo 0) {
        val bulkRequest = BulkRequest()

        val requests = database.from(Images)
            .select()
            .limit(1000)
            .offset(1000 * i)
            .map { row ->
                IndexRequest("memes")
                    .id(row[Images.fileId])
                    .source(
                        "text", row[Images.words]!!.joinToString(separator = " ")
                    )
            }

        bulkRequest.add(requests)
        bulkRequest.timeout("2m")

        println("executing count: ${bulkRequest.requests().size}")
        it.bulk(bulkRequest, RequestOptions.DEFAULT)
    }
}
