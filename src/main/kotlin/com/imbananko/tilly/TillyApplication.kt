package com.imbananko.tilly

import com.imbananko.tilly.utility.SqlQueries
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.telegram.telegrambots.ApiContextInitializer

@SpringBootApplication
@EnableConfigurationProperties(SqlQueries::class)
class TillyApplication

fun main(args: Array<String>) {
    ApiContextInitializer.init()
    SpringApplication.run(TillyApplication::class.java, *args)
}
