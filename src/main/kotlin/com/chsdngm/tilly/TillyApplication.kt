package com.chsdngm.tilly

import com.chsdngm.tilly.config.TelegramProperties
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties

@SpringBootApplication
@EnableConfigurationProperties(TelegramProperties::class)
class TillyApplication

fun main(args: Array<String>) {
    SpringApplication.run(TillyApplication::class.java, *args)
}
