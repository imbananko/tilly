package com.chsdngm.tilly

import com.chsdngm.tilly.utility.SqlQueries
import com.sun.net.httpserver.HttpServer
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericToStringSerializer
import org.springframework.data.redis.serializer.RedisSerializer
import org.telegram.telegrambots.ApiContextInitializer
import java.io.PrintWriter
import java.net.InetSocketAddress

@SpringBootApplication
@EnableConfigurationProperties(SqlQueries::class)
class TillyApplication {
  @Bean
  fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Int> {
    val template = RedisTemplate<String, Int>()
    template.connectionFactory = connectionFactory
    template.keySerializer = RedisSerializer.string()
    template.valueSerializer = GenericToStringSerializer(Integer::class.java)
    return template
  }
}

fun main(args: Array<String>) {
  ApiContextInitializer.init()
  SpringApplication.run(TillyApplication::class.java, *args)

  HttpServer.create(InetSocketAddress(4576), 0).apply {
    createContext("/hc") { http ->
      http.sendResponseHeaders(200, 0)
      PrintWriter(http.responseBody).use { out ->
        out.println("Health check is OK!")
      }
    }

    start()
  }
}
