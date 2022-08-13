package com.chsdngm.tilly.handlers

import java.util.concurrent.CompletableFuture

interface AbstractHandler<T> {
    fun handle(update: T): CompletableFuture<Void>
}
