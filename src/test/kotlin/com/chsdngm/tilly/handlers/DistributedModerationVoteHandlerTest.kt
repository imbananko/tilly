package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.model.DistributedModerationVoteUpdate
import com.chsdngm.tilly.model.DistributedModerationVoteValue
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.model.dto.Vote
import com.chsdngm.tilly.repository.DistributedModerationEventDao
import com.chsdngm.tilly.repository.VoteDao
import javassist.NotFoundException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verifyBlocking
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption

class DistributedModerationVoteHandlerTest {
    private val distributedModerationEventDao = mock<DistributedModerationEventDao>()
    private val voteDao = mock<VoteDao>()
    private val api = mock<TelegramApi>()

    private val distributedModerationVoteHandler =
        DistributedModerationVoteHandler(distributedModerationEventDao, voteDao, api)

    @Test
    fun shouldApproveOnPositiveDistributedModeration() {
        val update = mock<DistributedModerationVoteUpdate> {
            on(it.userId).thenReturn(777)
            on(it.messageId).thenReturn(666)
            on(it.voteValue).thenReturn(DistributedModerationVoteValue.APPROVE_DISTRIBUTED)
        }

        distributedModerationEventDao.stub {
            onBlocking { findMemeId(777, 666) }.thenReturn(1010)
        }

        distributedModerationVoteHandler.handleSync(update)

        verifyBlocking(distributedModerationEventDao) { findMemeId(777, 666) }
        val editMessageMethod = EditMessageCaption().apply {
            chatId = "777"
            messageId = 666
            caption = "вы одобрили этот мем"
        }
        verifyBlocking(api) { executeSuspended(editMessageMethod) }
        verifyBlocking(voteDao) { insert(Vote(1010, 777, 777, VoteValue.UP)) }

        verifyNoMoreInteractions(distributedModerationEventDao, voteDao, api)
    }

    @Test
    fun shouldDeclineOnNegativeDistributedModeration() {
        val update = mock<DistributedModerationVoteUpdate> {
            on(it.userId).thenReturn(777)
            on(it.messageId).thenReturn(666)
            on(it.voteValue).thenReturn(DistributedModerationVoteValue.DECLINE_DISTRIBUTED)
        }

        distributedModerationEventDao.stub {
            onBlocking { findMemeId(777, 666) }.thenReturn(1010)
        }

        distributedModerationVoteHandler.handleSync(update)

        verifyBlocking(distributedModerationEventDao) { findMemeId(777, 666) }
        val editMessageMethod = EditMessageCaption().apply {
            chatId = "777"
            messageId = 666
            caption = "вы засрали этот мем"
        }
        verifyBlocking(api) { executeSuspended(editMessageMethod) }
        verifyBlocking(voteDao) { insert(Vote(1010, 777, 777, VoteValue.DOWN)) }
        verifyNoMoreInteractions(distributedModerationEventDao, voteDao, api)
    }

    @Test
    fun shouldThrowWhenMemeNotFound() {
        val update = mock<DistributedModerationVoteUpdate> {
            on(it.userId).thenReturn(777)
            on(it.messageId).thenReturn(666)
            on(it.voteValue).thenReturn(DistributedModerationVoteValue.APPROVE_DISTRIBUTED)

        }
        distributedModerationEventDao.stub {
            onBlocking { findMemeId(777, 666) }.thenReturn(null)
        }

        assertThrows<NotFoundException> { distributedModerationVoteHandler.handleSync(update) }
    }
}