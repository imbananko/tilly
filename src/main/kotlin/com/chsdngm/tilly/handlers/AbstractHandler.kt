package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.Timestampable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

interface AbstractHandler<T : Timestampable> {
    fun handleSync(update: T)

    fun handle(update: T): CompletableFuture<Void> = CompletableFuture
        .supplyAsync({ handleSync(update) }, getExecutor())
        .thenAcceptAsync {
            measureTime(update)
        }

    fun measureTime(update: T) {}

    fun getExecutor(): ExecutorService = Executors.newSingleThreadExecutor()
}
