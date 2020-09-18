package com.chsdngm.tilly.handlers

import org.telegram.telegrambots.meta.api.objects.Update

interface AbstractHandler<ConcreteUpdate> {
  fun match(update: Update): Boolean
  fun handle(update: ConcreteUpdate)
  fun transform(update: Update): ConcreteUpdate

  fun handle(update: Update) {
    handle(transform(update))
  }
}
