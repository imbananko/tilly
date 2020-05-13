package com.chsdngm.tilly.repository

import com.chsdngm.tilly.utility.SqlQueries
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.io.File

@Repository
class ImageRepository(private val template: NamedParameterJdbcTemplate,
                      private val queries: SqlQueries) {
  fun saveImage(file: File, fileId: String) =
      template.update(queries.getFromConfOrFail("insertImage"),
          MapSqlParameterSource("fileId", fileId)
              .addValue("file", file.readBytes()))

  fun findAll() = template.query("select file_id, file from image")
  { rs, _ -> rs.getString("file_id") to rs.getBinaryStream("file") }.toMap()
}