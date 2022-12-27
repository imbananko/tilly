package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.Timestampable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

abstract class AbstractHandler<T : Timestampable>(private val executorService: ExecutorService) {

    abstract fun handleSync(update: T)

    fun handle(update: T): CompletableFuture<Void> = CompletableFuture
        .supplyAsync({ handleSync(update) }, executorService)
        .thenAcceptAsync {
            measureTime(update)
        }

    open fun measureTime(update: T) {}
}
