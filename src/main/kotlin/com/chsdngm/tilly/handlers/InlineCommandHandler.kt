package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.InlineCommandUpdate
import com.chsdngm.tilly.similarity.ImageMatcher
import com.chsdngm.tilly.utility.DocumentPage
import com.chsdngm.tilly.utility.TillyConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.cached.InlineQueryResultCachedPhoto

@Service
class InlineCommandHandler(val matcher: ImageMatcher) : AbstractHandler<InlineCommandUpdate> {
  private val log = LoggerFactory.getLogger(javaClass)

  val chunkSize = 10

  override fun handle(update: InlineCommandUpdate) {
    if (update.value.isBlank()) return

    val offset = if (update.offset.isBlank()) 0 else update.offset.toInt()

    val memes = matcher
      .find(update.value, DocumentPage(offset, chunkSize))
      .map {
        InlineQueryResultCachedPhoto().apply {
          photoFileId = it.id
          id = it.id.take(64)
          description = it.body
        }
      }.take(chunkSize)

    AnswerInlineQuery().apply {
      inlineQueryId = update.id
      nextOffset = "${offset + 1}"
      results = memes
    }.let { TillyConfig.api.execute(it) }

    log.info("Searched memes with text: ${update.value}, reached page: $offset")
  }
}