package com.chsdngm.tilly

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Configuration
class Configuration {
    @Bean
    fun forkJoinPool(): ExecutorService {
        return Executors.newWorkStealingPool()
    }
}