package com.imbananko.tilly.utility

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sql")
data class SqlQueries(val queries: Map<String, String>) {
  fun getFromConfOrFail(key: String): String =
      this.queries[key] ?: error("Configuration should contain `$key`")
}
