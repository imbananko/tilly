package com.chsdngm.tilly.handlers


//@RunWith(SpringJUnit4ClassRunner::class)
class CommandHandlerTest {


//  @MockBean
//  private lateinit var voteRepository: VoteRepository
//
//  @SpyBean
//  private lateinit var handler: CommandHandler
//
//  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
//  val update: Update = mock(Update::class.java)
//
//  @Test
//  fun shouldSendInfoMessageWhenHelpCommandReceived() {
//    val botConfig = mock(BotConfig::class.java)
//    `when`(update.message.text).thenReturn("/help")
//    `when`(update.message.chatId).thenReturn(0L)
////    `when`(botConfig.BOT_USERNAME).thenReturn("@meme_manager")
//    given(botConfig.BOT_USERNAME).willReturn("@meme_manager")
//
//    val sendMessageMethod = SendMessage(0L, handler.infoText).enableHtml(true)
//    doReturn(Message()).`when`(botConfig.api).execute(sendMessageMethod)
//
//    handler.handle(CommandUpdate(this.update))
//    verify(botConfig.api, times(1)).execute(sendMessageMethod)
//  }
//
//  @Test
//  fun shouldSendInfoMessageWhenStartCommandReceived() {
//    `when`(update.message.text).thenReturn("/start")
//    `when`(update.message.chatId).thenReturn(0L)
//
//    val sendMessageMethod = SendMessage(0L, handler.infoText).enableHtml(true)
//    doReturn(Message()).`when`(api).execute(sendMessageMethod)
//
//    handler.handle(CommandUpdate(this.update))
//    verify(api, times(1)).execute(sendMessageMethod)
//  }
}