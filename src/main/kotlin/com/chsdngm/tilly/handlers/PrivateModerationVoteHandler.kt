package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.*
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.utility.TillyConfig
import com.chsdngm.tilly.utility.TillyConfig.Companion.BETA_CHAT_ID
import com.chsdngm.tilly.utility.createMarkup
import com.chsdngm.tilly.utility.mention
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User

@Service
class PrivateModerationVoteHandler(private val memeRepository: MemeRepository) : AbstractHandler<PrivateVoteUpdate> {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional
  override fun handle(update: PrivateVoteUpdate) {
    memeRepository.findMemeByModerationChatIdAndModerationChatMessageId(update.user.id.toLong(), update.messageId)?.let {
      when (update.voteValue) {
        PrivateVoteValue.APPROVE -> approve(update, it)
        PrivateVoteValue.DECLINE -> decline(update, it)
      }
    } ?: log.error("cannot find meme by messageId=${update.messageId}")
  }

  private fun approve(update: PrivateVoteUpdate, meme: Meme) {
    EditMessageCaption()
        .setChatId(update.user.id.toString())
        .setMessageId(update.messageId)
        .setCaption("мем отправлен на канал")
        .let { TillyConfig.api.execute(it) }

    meme.votes.add(Vote(meme.id, update.user.id, update.user.id.toLong(), VoteValue.UP))

    meme.channelMessageId = SendPhoto()
        .setChatId(TillyConfig.CHANNEL_ID)
        .setPhoto(meme.fileId)
        .setReplyMarkup(createMarkup(meme.votes.groupingBy { it.value }.eachCount()))
        .setCaption(meme.caption)
        .let { TillyConfig.api.execute(it) }.messageId

    EditMessageText()
        .setChatId(meme.senderId.toString())
        .setMessageId(meme.privateReplyMessageId)
        .setText("мем отправлен на канал. статистика: \n\n${VoteValue.UP.emoji}: 1").let { TillyConfig.api.execute(it) }

    log.info("ranked moderator with id=${update.user.id} approved meme=$meme")
    sendPrivateModerationEventToBeta(meme, update.user, PrivateVoteValue.APPROVE)
  }

  private fun decline(update: PrivateVoteUpdate, meme: Meme) {
    EditMessageCaption()
        .setChatId(update.user.id.toString())
        .setMessageId(update.messageId)
        .setCaption("мем предан забвению")
        .let { TillyConfig.api.execute(it) }

    //TODO fix long/int types in whole project
    meme.votes.add(Vote(meme.id, update.user.id, update.user.id.toLong(), VoteValue.DOWN))
    memeRepository.save(meme)

    EditMessageText()
        .setChatId(meme.senderId.toString())
        .setMessageId(meme.privateReplyMessageId)
        .setText("мем предан забвению").let { TillyConfig.api.execute(it) }

    log.info("ranked moderator with id=${update.user.id} declined meme=$meme")
    sendPrivateModerationEventToBeta(meme, update.user, PrivateVoteValue.DECLINE)
  }

  private fun sendPrivateModerationEventToBeta(meme: Meme, moderator: User, solution: PrivateVoteValue) {
    val caption = "${moderator.mention()} " +
        if (solution == PrivateVoteValue.APPROVE) "отправил(а) мем на канал"
        else "предал(а) мем забвению"

    SendPhoto()
        .setChatId(BETA_CHAT_ID)
        .setPhoto(meme.fileId)
        .setCaption(caption)
        .setParseMode(ParseMode.HTML)
        .let { TillyConfig.api.execute(it) }
  }

  override fun match(update: Update) = update.hasCallbackQuery() && update.callbackQuery.message.chat.isUserChat
      && setOf(*PrivateVoteValue.values()).map { it.name }.contains(update.callbackQuery.data)

  override fun transform(update: Update) = PrivateVoteUpdate(update)
}