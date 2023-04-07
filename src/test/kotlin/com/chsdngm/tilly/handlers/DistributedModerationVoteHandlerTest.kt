package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.TelegramApi
import com.chsdngm.tilly.model.DistributedModerationVoteUpdate
import com.chsdngm.tilly.model.DistributedModerationVoteValue
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.model.dto.Vote
import com.chsdngm.tilly.repository.DistributedModerationEventDao
import com.chsdngm.tilly.repository.VoteDao
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption

class DistributedModerationVoteHandlerTest {
    private val distributedModerationEventDao = mock(DistributedModerationEventDao::class.java)
    private val voteDao = mock(VoteDao::class.java)
    private val api = mock(TelegramApi::class.java)

    private val distributedModerationVoteHandler = DistributedModerationVoteHandler(distributedModerationEventDao, voteDao, api)

    @Test
    fun shouldApproveOnPositiveDistributedModeration() {
        val update = mock(DistributedModerationVoteUpdate::class.java).apply {
            `when`(userId).thenReturn(777)
            `when`(messageId).thenReturn(666)
            `when`(voteValue).thenReturn(DistributedModerationVoteValue.APPROVE_DISTRIBUTED)
        }

        `when`(distributedModerationEventDao.findMemeId(777, 666)).thenReturn(1010)

        val editMessageMethod = EditMessageCaption().apply {
            chatId = "777"
            messageId = 666
            caption = "вы одобрили этот мем"
        }

        distributedModerationVoteHandler.handleSync(update)
        verify(distributedModerationEventDao).findMemeId(777, 666)
        verify(api).execute(editMessageMethod)
        verify(voteDao).insert(Vote(1010, 777, 777, VoteValue.UP))
    }

    @Test
    fun shouldDeclineOnNegativeDistributedModeration() {
        val update = mock(DistributedModerationVoteUpdate::class.java).apply {
            `when`(userId).thenReturn(777)
            `when`(messageId).thenReturn(666)
            `when`(voteValue).thenReturn(DistributedModerationVoteValue.DECLINE_DISTRIBUTED)
        }

        `when`(distributedModerationEventDao.findMemeId(777, 666)).thenReturn(1010)

        val editMessageMethod = EditMessageCaption().apply {
            chatId = "777"
            messageId = 666
            caption = "вы засрали этот мем"
        }

        distributedModerationVoteHandler.handleSync(update)
        verify(distributedModerationEventDao).findMemeId(777, 666)
        verify(api).execute(editMessageMethod)
        verify(voteDao).insert(Vote(1010, 777, 777, VoteValue.DOWN))
    }
}