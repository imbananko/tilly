package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.config.TelegramConfig
import com.chsdngm.tilly.model.DistributedModerationVoteUpdate
import com.chsdngm.tilly.model.DistributedModerationVoteValue
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.model.dto.Vote
import com.chsdngm.tilly.repository.DistributedModerationDao
import com.chsdngm.tilly.repository.VoteDao
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Service
class DistributedModerationVoteHandler(private val distributedModerationDao: DistributedModerationDao,
                                       private val voteDao: VoteDao) : AbstractHandler<DistributedModerationVoteUpdate>() {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()

    override fun getExecutor(): ExecutorService = executorService

    override fun handleSync(update: DistributedModerationVoteUpdate) {
        distributedModerationDao.findMemeId(update.user.id, update.messageId)?.let {
            when (update.voteValue) {
                DistributedModerationVoteValue.APPROVE_DISTRIBUTED -> approve(update, it)
                DistributedModerationVoteValue.DECLINE_DISTRIBUTED -> decline(update, it)
            }
        } ?: log.error("unknown voteValue=${update.voteValue}")
    }

    private fun approve(update: DistributedModerationVoteUpdate, memeId: Int) {
        EditMessageCaption().apply {
            chatId = update.user.id.toString()
            messageId = update.messageId
            caption = "вы одобрили этот мем"
        }.let { TelegramConfig.api.execute(it) }

        voteDao.insert(Vote(memeId, update.user.id, update.user.id, VoteValue.UP))

        log.info("moderator with id=${update.user.id} voted up for meme memeId=$memeId")
    }

    private fun decline(update: DistributedModerationVoteUpdate, memeId: Int) {
        EditMessageCaption().apply {
            chatId = update.user.id.toString()
            messageId = update.messageId
            caption = "вы засрали этот мем"
        }.let { TelegramConfig.api.execute(it) }

        voteDao.insert(Vote(memeId, update.user.id, update.user.id, VoteValue.DOWN))

        log.info("moderator with id=${update.user.id} voted down for meme memeId=$memeId")
    }
}