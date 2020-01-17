package com.imbananko.tilly

import com.imbananko.tilly.utility.SqlQueries
import com.sun.net.httpserver.HttpServer
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.telegram.telegrambots.ApiContextInitializer
import java.io.PrintWriter
import java.net.InetSocketAddress

@SpringBootApplication
@EnableConfigurationProperties(SqlQueries::class)
class TillyApplication

fun main(args: Array<String>) {
  ApiContextInitializer.init()
  SpringApplication.run(TillyApplication::class.java, *args)

  HttpServer.create(InetSocketAddress(8080), 0).apply {
    createContext("/hc") { http ->
      http.sendResponseHeaders(200, 0)
      PrintWriter(http.responseBody).use { out ->
        out.println("Health check is OK!")
      }
    }

    start()
  }
}
