package com.chsdngm.tilly

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.telegram.telegrambots.ApiContextInitializer

@SpringBootApplication(proxyBeanMethods=false)
class TillyApplication

fun main(args: Array<String>) {
  ApiContextInitializer.init()
  SpringApplication.run(TillyApplication::class.java, *args)
}
