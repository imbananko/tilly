package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.model.DistributedModerationVoteUpdate
import com.chsdngm.tilly.model.DistributedModerationVoteValue
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.model.dto.Vote
import com.chsdngm.tilly.repository.DistributedModerationEventDao
import com.chsdngm.tilly.repository.VoteDao
import javassist.NotFoundException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption
import org.telegram.telegrambots.meta.api.objects.Update

@Service
class DistributedModerationVoteHandler(
    private val distributedModerationEventDao: DistributedModerationEventDao,
    private val voteDao: VoteDao,
    private val api: TelegramApi
) : AbstractHandler<DistributedModerationVoteUpdate>() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun handleSync(update: DistributedModerationVoteUpdate) = runBlocking {
        val memeId = distributedModerationEventDao.findMemeId(update.userId, update.messageId)
            ?: throw NotFoundException("Meme wasn't found. update=$update")

        when (update.voteValue) {
            DistributedModerationVoteValue.APPROVE_DISTRIBUTED -> approve(update, memeId)
            DistributedModerationVoteValue.DECLINE_DISTRIBUTED -> decline(update, memeId)
        }
    }

    private suspend fun approve(update: DistributedModerationVoteUpdate, memeId: Int) = coroutineScope {
        val editMessageCaption = EditMessageCaption().apply {
            chatId = update.userId.toString()
            messageId = update.messageId
            caption = "вы одобрили этот мем"
        }

        launch { api.executeSuspended(editMessageCaption) }
        launch { voteDao.insert(Vote(memeId, update.userId, update.userId, VoteValue.UP)) }

        log.info("moderator with id=${update.userId} voted up for meme memeId=$memeId")
    }

    private suspend fun decline(update: DistributedModerationVoteUpdate, memeId: Int) = coroutineScope {
        val editMessageCaption = EditMessageCaption().apply {
            chatId = update.userId.toString()
            messageId = update.messageId
            caption = "вы засрали этот мем"
        }
        launch { api.executeSuspended(editMessageCaption) }
        launch { voteDao.insert(Vote(memeId, update.userId, update.userId, VoteValue.DOWN)) }

        log.info("moderator with id=${update.userId} voted down for meme memeId=$memeId")
    }

    override fun retrieveSubtype(update: Update) = if (update.hasCallbackQuery()
        && update.callbackQuery.message.chat.isUserChat
        && DistributedModerationVoteValue.values().map { it.name }.contains(update.callbackQuery.data)
    ) {
        DistributedModerationVoteUpdate(update)
    } else null
}