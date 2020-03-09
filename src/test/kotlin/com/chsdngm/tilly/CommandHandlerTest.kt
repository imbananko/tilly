package com.chsdngm.tilly

import com.chsdngm.tilly.handlers.CommandHandler
import com.chsdngm.tilly.model.CommandUpdate
import com.chsdngm.tilly.repository.VoteRepository
import com.chsdngm.tilly.utility.BotConfigImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito.*
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update

@RunWith(SpringJUnit4ClassRunner::class)
class CommandHandlerTest {
  @MockBean
  private lateinit var config: BotConfigImpl

  @MockBean
  private lateinit var voteRepository: VoteRepository

  @SpyBean
  private lateinit var handler: CommandHandler

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  val update: Update = mock(Update::class.java)

  @Test
  fun shouldReturn() {
    `when`(update.message.text).thenReturn("/help")
    `when`(update.message.chatId).thenReturn(0L)
    `when`(config.username).thenReturn("@meme_manager")

    val sendMessageMethod = SendMessage(0L, handler.infoText).enableHtml(true)
    doReturn(Message()).`when`(handler).execute(sendMessageMethod)

    handler.handle(CommandUpdate(this.update))
    verify(handler, times(1)).execute(sendMessageMethod)
  }
}