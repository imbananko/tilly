package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.Timestampable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

abstract class AbstractHandler<T : Timestampable> {
    private val executorService = Executors.newWorkStealingPool()

    abstract fun handleSync(update: T)

    fun handle(update: T): CompletableFuture<Void> = CompletableFuture
        .supplyAsync({ handleSync(update) }, getExecutor())
        .thenAcceptAsync {
            measureTime(update)
        }

    open fun measureTime(update: T) {}

    open fun getExecutor(): ExecutorService = executorService
}
