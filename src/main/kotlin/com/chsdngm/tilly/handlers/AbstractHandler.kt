package com.chsdngm.tilly.handlers

interface AbstractHandler<T> {
    fun handle(update: T)
}
