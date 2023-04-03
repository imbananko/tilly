package com.chsdngm.tilly

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class TillyApplication

fun main(args: Array<String>) {
    SpringApplication.run(TillyApplication::class.java, *args)
}
