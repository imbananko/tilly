package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.Timestampable
import org.telegram.telegrambots.meta.api.objects.Update
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.ForkJoinPool.commonPool

abstract class AbstractHandler<T>(
    private val executorService: ExecutorService = commonPool()
) where T : Timestampable {
    abstract fun handleSync(update: T)

    fun handle(update: T): CompletableFuture<Void> = CompletableFuture
        .supplyAsync({ handleSync(update) }, executorService)
        .thenAcceptAsync {
            measureTime(update)
        }

    open fun measureTime(update: T) {}
    abstract fun retrieveSubtype(update: Update): T?
}
