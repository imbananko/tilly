package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.*
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.utility.TillyConfig
import com.chsdngm.tilly.utility.createMarkup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption

@Service
class PrivateModerationVoteHandler(private val memeRepository: MemeRepository) : AbstractHandler<PrivateVoteUpdate> {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional
  override fun handle(update: PrivateVoteUpdate) {
    memeRepository.findMemeByChatMessageId(update.messageId)?.let {
      when(update.voteValue) {
        PrivateVoteValue.APPROVE -> approve(update, it)
        PrivateVoteValue.DECLINE -> decline(update, it)
      }
    } ?: log.error("cannot find meme by messageId=${update.messageId}")
  }

  private fun approve(update: PrivateVoteUpdate, meme: Meme) {
    EditMessageCaption()
        .setChatId(update.senderId.toString())
        .setMessageId(update.messageId)
        .setCaption("мем отправлен на канал")
        .let { TillyConfig.api.execute(it) }

    meme.votes.add(Vote(update.messageId, update.senderId.toInt(), VoteValue.UP, VoteSourceType.PRIVATE_CHAT))

    SendPhoto()
        .setChatId(TillyConfig.CHANNEL_ID)
        .setPhoto(meme.fileId)
        .setReplyMarkup(createMarkup(meme.votes.groupingBy { it.value }.eachCount()))
        .setCaption(meme.caption)
        .let { TillyConfig.api.execute(it) }.also {
          memeRepository.save(meme.copy(channelMessageId = it.messageId))
        }

    log.info("ranked moderator with id=${update.senderId} approved meme=$meme")
  }

  private fun decline(update: PrivateVoteUpdate, meme: Meme) {
    EditMessageCaption()
        .setChatId(update.senderId.toString())
        .setMessageId(update.messageId)
        .setCaption("мем предан забвению")
        .let { TillyConfig.api.execute(it) }

    //TODO fix long/int types in whole project
    meme.votes.add(Vote(update.messageId, update.senderId.toInt(), VoteValue.DOWN, VoteSourceType.PRIVATE_CHAT))

    log.info("ranked moderator with id=${update.senderId} declined meme=$meme")
  }
}