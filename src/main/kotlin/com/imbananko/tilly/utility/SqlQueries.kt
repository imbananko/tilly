package com.imbananko.tilly.utility

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sql")
data class SqlQueries(val queries: Map<String, String>)

