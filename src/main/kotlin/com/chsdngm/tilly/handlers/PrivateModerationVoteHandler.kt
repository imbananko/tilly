package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.config.TelegramConfig
import com.chsdngm.tilly.config.TelegramConfig.Companion.BETA_CHAT_ID
import com.chsdngm.tilly.model.MemeStatus
import com.chsdngm.tilly.model.PrivateVoteUpdate
import com.chsdngm.tilly.model.PrivateVoteValue
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.model.dto.Meme
import com.chsdngm.tilly.model.dto.Vote
import com.chsdngm.tilly.repository.MemeDao
import com.chsdngm.tilly.repository.VoteDao
import com.chsdngm.tilly.utility.mention
import com.chsdngm.tilly.utility.updateStatsInSenderChat
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.User

@Service
class PrivateModerationVoteHandler(private val memeDao: MemeDao, private val voteDao: VoteDao) :
    AbstractHandler<PrivateVoteUpdate>() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun handleSync(update: PrivateVoteUpdate) {
        memeDao.findMemeByModerationChatIdAndModerationChatMessageId(update.user.id, update.messageId)
            ?.let {
                when (update.voteValue) {
                    PrivateVoteValue.APPROVE -> approve(update, it.first, it.second)
                    PrivateVoteValue.DECLINE -> decline(update, it.first, it.second)
                }
            } ?: log.error("unknown voteValue=${update.voteValue}")

        log.info("processed private vote update=$update")
    }

    private fun approve(update: PrivateVoteUpdate, meme: Meme, votes: List<Vote>) {
        EditMessageCaption().apply {
            chatId = update.user.id.toString()
            messageId = update.messageId
            caption = "мем будет отправлен на канал"
        }.let { TelegramConfig.api.execute(it) }

        meme.status = MemeStatus.SCHEDULED
        memeDao.update(meme)
        voteDao.insert(Vote(meme.id, update.user.id, update.user.id, VoteValue.UP))

        updateStatsInSenderChat(meme, votes)

        log.info("ranked moderator with id=${update.user.id} approved meme=$meme")
        sendPrivateModerationEventToBeta(meme, update.user, PrivateVoteValue.APPROVE)
    }

    private fun decline(update: PrivateVoteUpdate, meme: Meme, votes: List<Vote>) {
        EditMessageCaption().apply {
            chatId = update.user.id.toString()
            messageId = update.messageId
            caption = "мем предан забвению"
        }.let { TelegramConfig.api.execute(it) }

        meme.status = MemeStatus.DECLINED
        memeDao.update(meme)
        voteDao.insert(Vote(meme.id, update.user.id, update.user.id, VoteValue.DOWN))

        updateStatsInSenderChat(meme, votes)

        log.info("ranked moderator with id=${update.user.id} declined meme=$meme")
        sendPrivateModerationEventToBeta(meme, update.user, PrivateVoteValue.DECLINE)
    }

    private fun sendPrivateModerationEventToBeta(meme: Meme, moderator: User, solution: PrivateVoteValue) {
        val memeCaption =
            "${moderator.mention()} " + if (solution == PrivateVoteValue.APPROVE) "отправил(а) мем на канал"
            else "предал(а) мем забвению"

        SendPhoto().apply {
            chatId = BETA_CHAT_ID
            photo = InputFile(meme.fileId)
            caption = memeCaption
            parseMode = ParseMode.HTML
            disableNotification = true
        }.let { TelegramConfig.api.execute(it) }
    }
}