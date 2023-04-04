package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.model.DistributedModerationVoteUpdate
import com.chsdngm.tilly.model.DistributedModerationVoteValue
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.model.dto.Vote
import com.chsdngm.tilly.repository.DistributedModerationEventDao
import com.chsdngm.tilly.repository.VoteDao
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption

@Service
class DistributedModerationVoteHandler(
        private val distributedModerationEventDao: DistributedModerationEventDao,
        private val voteDao: VoteDao,
        private val api: TelegramApi
) : AbstractHandler<DistributedModerationVoteUpdate>() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun handleSync(update: DistributedModerationVoteUpdate) {
        distributedModerationEventDao.findMemeId(update.userId, update.messageId)?.let {
            when (update.voteValue) {
                DistributedModerationVoteValue.APPROVE_DISTRIBUTED -> approve(update, it)
                DistributedModerationVoteValue.DECLINE_DISTRIBUTED -> decline(update, it)
            }
        } ?: log.error("unknown voteValue=${update.voteValue}")
    }

    private fun approve(update: DistributedModerationVoteUpdate, memeId: Int) {
        EditMessageCaption().apply {
            chatId = update.userId.toString()
            messageId = update.messageId
            caption = "вы одобрили этот мем"
        }.let { api.execute(it) }

        //TODO fix
        voteDao.insert(Vote(memeId, update.userId, update.userId, VoteValue.UP))

        log.info("moderator with id=${update.userId} voted up for meme memeId=$memeId")
    }

    private fun decline(update: DistributedModerationVoteUpdate, memeId: Int) {
        EditMessageCaption().apply {
            chatId = update.userId.toString()
            messageId = update.messageId
            caption = "вы засрали этот мем"
        }.let { api.execute(it) }

        //TODO fix
        voteDao.insert(Vote(memeId, update.userId, update.userId, VoteValue.DOWN))

        log.info("moderator with id=${update.userId} voted down for meme memeId=$memeId")
    }

    override fun getUpdateType() = DistributedModerationVoteUpdate::class
}